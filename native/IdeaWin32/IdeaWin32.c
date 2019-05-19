// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include "IdeaWin32.h"
#include <stdbool.h>
#include <windows.h>

typedef DWORD (WINAPI *GetFinalPathNameByHandlePtr)(HANDLE, LPCWSTR, DWORD, DWORD);
static GetFinalPathNameByHandlePtr __GetFinalPathNameByHandle = NULL;

static jfieldID nameID = NULL;
static jfieldID attributesID = NULL;
static jfieldID timestampID = NULL;
static jfieldID lengthID = NULL;

#define FILE_INFO_CLASS "com/intellij/openapi/util/io/win32/FileInfo"
#define BROKEN_SYMLINK_ATTR ((DWORD)-1)
#define IS_SET(flags, flag) (((flags) & (flag)) == (flag))
#define FILE_SHARE_ALL (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)

static wchar_t *ToWinPath(JNIEnv *env, jstring path, boolean dirSuffix);
static jobject CreateFileInfo(JNIEnv *env, wchar_t *path, boolean isDirectory, LPWIN32_FIND_DATAW lpData, jclass aClass);
static jobjectArray CopyObjectArray(JNIEnv *env, jobjectArray src, jclass aClass, jsize count, jsize newSize);


// interface methods

JNIEXPORT void JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_initIDs(JNIEnv *env, jclass cls) {
  __GetFinalPathNameByHandle = (GetFinalPathNameByHandlePtr)GetProcAddress(GetModuleHandleW(L"kernel32.dll"), "GetFinalPathNameByHandleW");

  jclass fileInfoClass = (*env)->FindClass(env, FILE_INFO_CLASS);
  if (fileInfoClass == NULL) {
    return;
  }

  nameID = (*env)->GetFieldID(env, fileInfoClass, "name", "Ljava/lang/String;");
  attributesID = (*env)->GetFieldID(env, fileInfoClass, "attributes", "I");
  timestampID = (*env)->GetFieldID(env, fileInfoClass, "timestamp", "J");
  lengthID = (*env)->GetFieldID(env, fileInfoClass, "length", "J");
}

JNIEXPORT jobject JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_getInfo0(JNIEnv *env, jobject method, jstring path) {
  wchar_t *winPath = ToWinPath(env, path, false);
  if (winPath == NULL) {
    return NULL;
  }

  WIN32_FILE_ATTRIBUTE_DATA attrData;
  BOOL res = GetFileAttributesExW(winPath, GetFileExInfoStandard, &attrData);
  if (!res) {
    free(winPath);
    return NULL;
  }

  jclass fileInfoClass = (*env)->FindClass(env, FILE_INFO_CLASS);
  if (fileInfoClass == NULL) {
    return NULL;
  }

  jobject result = NULL;
  if (IS_SET(attrData.dwFileAttributes, FILE_ATTRIBUTE_REPARSE_POINT)) {
    // may be symlink
    WIN32_FIND_DATAW data;
    HANDLE h = FindFirstFileW(winPath, &data);
    if (h != INVALID_HANDLE_VALUE) {
      FindClose(h);
      result = CreateFileInfo(env, winPath, false, &data, fileInfoClass);
    }
  }
  if (result == NULL) {
    // either not a symlink or FindFirstFile() failed
    WIN32_FIND_DATAW data;
    data.dwFileAttributes = attrData.dwFileAttributes;
    data.dwReserved0 = 0;
    data.ftLastWriteTime = attrData.ftLastWriteTime;
    data.nFileSizeLow = attrData.nFileSizeLow;
    data.nFileSizeHigh = attrData.nFileSizeHigh;
    data.cFileName[0] = L'\0';
    result = CreateFileInfo(env, winPath, false, &data, fileInfoClass);
  }

  free(winPath);
  return result;
}

JNIEXPORT jstring JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_resolveSymLink0(JNIEnv *env, jobject method, jstring path) {
  if (__GetFinalPathNameByHandle == NULL) {
    return path;  // links not supported
  }

  wchar_t *winPath = ToWinPath(env, path, false);
  if (winPath == NULL) {
    return NULL;
  }

  if (wcsncmp(winPath, L"\\\\?\\UNC\\", 8) == 0) {
    free(winPath);
    return path;
  }

  jstring result = path;

  HANDLE h = CreateFileW(winPath, 0, FILE_SHARE_ALL, NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, NULL);
  if (h != INVALID_HANDLE_VALUE) {
    wchar_t buff[MAX_PATH], *finalPath = buff;
    DWORD len = __GetFinalPathNameByHandle(h, buff, MAX_PATH, 0);
    if (len >= MAX_PATH) {
      finalPath = (wchar_t*)calloc(len + 1, sizeof(wchar_t));
      len = finalPath != NULL ? __GetFinalPathNameByHandle(h, finalPath, len, 0) : 0;
    }
    if (len > 0 && finalPath != NULL) {
      int prefix = 0;
      if (len > 8 && wcsncmp(finalPath, L"\\\\?\\UNC\\", 8) == 0) {
        prefix = 6;
        finalPath[6] = L'\\';
      } else if (len > 4 && finalPath[0] == L'\\' && finalPath[1] == L'\\' && finalPath[2] == L'?' && finalPath[3] == L'\\') {
        prefix = 4;
      }
      result = (*env)->NewString(env, (jchar*)finalPath + prefix, len - prefix);
      if (finalPath != buff) {
        free(finalPath);
      }
    }
    CloseHandle(h);
  } else {
    result = NULL;
  }

  free(winPath);
  return result;
}

JNIEXPORT jobjectArray JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_listChildren0(JNIEnv *env, jobject method, jstring path) {
  jclass fileInfoClass = (*env)->FindClass(env, FILE_INFO_CLASS);
  if (fileInfoClass == NULL) {
    return NULL;
  }

  wchar_t *winPath = ToWinPath(env, path, true);
  if (winPath == NULL) {
    return NULL;
  }

  WIN32_FIND_DATAW data;
  HANDLE h = FindFirstFileW(winPath, &data);
  if (h == INVALID_HANDLE_VALUE) {
    free(winPath);
    return NULL;
  }

  jsize len = 0, maxLen = 16;
  jobjectArray result = (*env)->NewObjectArray(env, maxLen, fileInfoClass, NULL);
  if (result != NULL) {
    do {
      if (wcscmp(data.cFileName, L".") == 0 || wcscmp(data.cFileName, L"..") == 0) {
        continue;
      }

      if (len == maxLen) {
        result = CopyObjectArray(env, result, fileInfoClass, len, maxLen <<= 1);
        if (result == NULL) {
          break;
        }
      }

      jobject o = CreateFileInfo(env, winPath, true, &data, fileInfoClass);
      (*env)->SetObjectArrayElement(env, result, len++, o);
      (*env)->DeleteLocalRef(env, o);
    }
    while (FindNextFileW(h, &data));

    if (len != maxLen) {
      result = CopyObjectArray(env, result, fileInfoClass, len, len);
    }
  }

  free(winPath);
  FindClose(h);
  return result;
}


// utility methods

static LONGLONG pairToInt64(DWORD lowPart, DWORD highPart) {
    ULARGE_INTEGER large;
    large.LowPart = lowPart;
    large.HighPart = highPart;
    return large.QuadPart;
}

static wchar_t *ToWinPath(JNIEnv *env, jstring path, boolean dirSuffix) {
  size_t len = (size_t)((*env)->GetStringLength(env, path));
  const jchar *jstr = (*env)->GetStringChars(env, path, NULL);
  while (len > 0 && jstr[len - 1] == L'\\') --len;  // trim trailing separators

  if (len == 0) {
    (*env)->ReleaseStringChars(env, path, jstr);
    return NULL;
  }

  const wchar_t *prefix = L"\\\\?\\";
  size_t prefixLen = 4, skip = 0, suffixLen = dirSuffix ? 2 : 0;
  if (len == 2 && jstr[1] == L':') {
    prefix = L"";
    prefixLen = skip = 0;
  } else if (len > 2 && jstr[0] == L'\\' && jstr[1] == L'\\') {
    prefix = L"\\\\?\\UNC\\";
    prefixLen = 8;
    skip = 2;
  }

  wchar_t *pathBuf = (wchar_t*)calloc(prefixLen + len - skip + suffixLen + 1, sizeof(wchar_t));
  if (pathBuf != NULL) {
    if (prefixLen > 0) {
      wcsncpy_s(pathBuf, prefixLen + 1, prefix, prefixLen);
    }
    wcsncpy_s(pathBuf + prefixLen, len - skip + 1, (wchar_t*)jstr + skip, len - skip);
    if (suffixLen > 0) {
      wcsncpy_s(pathBuf + prefixLen + len - skip, suffixLen + 1, L"\\*", suffixLen);
    }
    pathBuf[prefixLen + len - skip + suffixLen] = L'\0';

    if (prefixLen > 0) {
      DWORD normLen = GetFullPathNameW(pathBuf, 0, NULL, NULL);
      if (normLen > 0) {
        wchar_t *normPathBuf = (wchar_t*)calloc(normLen, sizeof(wchar_t));
        if (normPathBuf != NULL) {
          GetFullPathNameW(pathBuf, normLen, normPathBuf, NULL);
          free(pathBuf);
          pathBuf = normPathBuf;
        }
      }
    }
  }

  (*env)->ReleaseStringChars(env, path, jstr);

  return pathBuf;
}

static jobject CreateFileInfo(JNIEnv *env, wchar_t *path, boolean isDirectory, LPWIN32_FIND_DATAW lpData, jclass aClass) {
  DWORD attributes = lpData->dwFileAttributes;
  LONGLONG timestamp = pairToInt64(lpData->ftLastWriteTime.dwLowDateTime, lpData->ftLastWriteTime.dwHighDateTime);
  LONGLONG length = pairToInt64(lpData->nFileSizeLow, lpData->nFileSizeHigh);

  if (IS_SET(attributes, FILE_ATTRIBUTE_REPARSE_POINT)) {
    if (IS_SET(lpData->dwReserved0, IO_REPARSE_TAG_SYMLINK) || IS_SET(lpData->dwReserved0, IO_REPARSE_TAG_MOUNT_POINT)) {
      attributes = BROKEN_SYMLINK_ATTR;
      timestamp = 0;
      length = 0;

      wchar_t *fullPath = path;
      if (isDirectory) {
        // trim '*' and append file name
        size_t dirLen = wcslen(path) - 1, nameLen = wcslen(lpData->cFileName), fullLen = dirLen + nameLen + 1;
        fullPath = (wchar_t*)calloc(fullLen, sizeof(wchar_t));
        if (fullPath == NULL) {
          return NULL;
        }
        wcsncpy_s(fullPath, dirLen + 1, path, dirLen);
        wcsncpy_s(fullPath + dirLen, nameLen + 1, lpData->cFileName, nameLen);
      }

      // read reparse point target attributes
      HANDLE h = CreateFileW(fullPath, 0, FILE_SHARE_ALL, NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, NULL);
      if (h != INVALID_HANDLE_VALUE) {
        BY_HANDLE_FILE_INFORMATION targetData;
        if (GetFileInformationByHandle(h, &targetData)) {
          attributes = targetData.dwFileAttributes | FILE_ATTRIBUTE_REPARSE_POINT;
          timestamp = pairToInt64(targetData.ftLastWriteTime.dwLowDateTime, targetData.ftLastWriteTime.dwHighDateTime);
          length = pairToInt64(targetData.nFileSizeLow, targetData.nFileSizeHigh);
        }
        CloseHandle(h);
      }

      if (fullPath != path) {
        free(fullPath);
      }
    } else {
      attributes &= (~ FILE_ATTRIBUTE_REPARSE_POINT);  // keep the flag only for known reparse points
    }
  }

  jobject o = (*env)->AllocObject(env, aClass);
  if (o == NULL) {
    return NULL;
  }

  jstring fileName = (*env)->NewString(env, (jchar*)lpData->cFileName, (jsize)wcslen(lpData->cFileName));
  if (fileName == NULL) {
    return NULL;
  }

  (*env)->SetObjectField(env, o, nameID, fileName);
  (*env)->SetIntField(env, o, attributesID, attributes);
  (*env)->SetLongField(env, o, timestampID, timestamp);
  (*env)->SetLongField(env, o, lengthID, length);

  return o;
}

static jobjectArray CopyObjectArray(JNIEnv *env, jobjectArray src, jclass aClass, jsize count, jsize newSize) {
  jobjectArray dst = (*env)->NewObjectArray(env, newSize, aClass, NULL);
  if (dst != NULL) {
    for (jsize i = 0; i < count; i++) {
      jobject o = (*env)->GetObjectArrayElement(env, src, i);
      (*env)->SetObjectArrayElement(env, dst, i, o);
      (*env)->DeleteLocalRef(env, o);
    }
  }
  (*env)->DeleteLocalRef(env, src);
  return dst;
}