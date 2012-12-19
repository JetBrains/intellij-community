/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "IdeaWin32.h"
#include <windows.h>

typedef DWORD (WINAPI *GetFinalPathNameByHandlePtr) (HANDLE, LPCWSTR, DWORD, DWORD dwFlags);
static GetFinalPathNameByHandlePtr __GetFinalPathNameByHandle = NULL;

static jfieldID nameID = NULL;
static jfieldID attributesID = NULL;
static jfieldID timestampID = NULL;
static jfieldID lengthID = NULL;

#define FILE_INFO_CLASS "com/intellij/openapi/util/io/win32/FileInfo"
#define BROKEN_SYMLINK_ATTR -1
#define IS_SET(flags, flag) ((flags & flag) == flag)
#define FILE_SHARE_ATTRIBUTES (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)

static wchar_t* ToWinPath(JNIEnv* env, jstring path, bool dirSuffix);
static jobject CreateFileInfo(JNIEnv* env, wchar_t* path, bool isDirectory, LPWIN32_FIND_DATA lpData, jclass aClass);
static jobjectArray CopyObjectArray(JNIEnv* env, jobjectArray src, jclass aClass, jsize count, jsize newSize);


// interface methods

JNIEXPORT void JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_initIDs(JNIEnv* env, jclass cls) {
    __GetFinalPathNameByHandle = (GetFinalPathNameByHandlePtr)GetProcAddress(GetModuleHandle(L"kernel32.dll"), "GetFinalPathNameByHandleW");

    jclass fileInfoClass = env->FindClass(FILE_INFO_CLASS);
    if (fileInfoClass == NULL) {
        return;
    }

    nameID = env->GetFieldID(fileInfoClass, "name", "Ljava/lang/String;");
    attributesID = env->GetFieldID(fileInfoClass, "attributes", "I");
    timestampID = env->GetFieldID(fileInfoClass, "timestamp", "J");
    lengthID = env->GetFieldID(fileInfoClass, "length", "J");
}

JNIEXPORT jobject JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_getInfo0(JNIEnv* env, jobject method, jstring path) {
    wchar_t* winPath = ToWinPath(env, path, false);
    if (winPath == NULL) {
        return NULL;
    }

    WIN32_FILE_ATTRIBUTE_DATA attrData;
    BOOL res = GetFileAttributesExW(winPath, GetFileExInfoStandard, &attrData);
    if (!res) {
        free(winPath);
        return NULL;
    }

    jclass fileInfoClass = env->FindClass(FILE_INFO_CLASS);
    if (fileInfoClass == NULL) {
        return NULL;
    }

    jobject result = NULL;
    if (IS_SET(attrData.dwFileAttributes, FILE_ATTRIBUTE_REPARSE_POINT)) {
        // may be symlink
        WIN32_FIND_DATA data;
        HANDLE h = FindFirstFileW(winPath, &data);
        if (h != INVALID_HANDLE_VALUE) {
            FindClose(h);
            result = CreateFileInfo(env, winPath, false, &data, fileInfoClass);
        }
    }
    if (result == NULL) {
        // either not a symlink or FindFirstFile() failed
        WIN32_FIND_DATA data;
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

JNIEXPORT jstring JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_resolveSymLink0(JNIEnv* env, jobject method, jstring path) {
    if (__GetFinalPathNameByHandle == NULL) {
        return path;  // links not supported
    }

    wchar_t* winPath = ToWinPath(env, path, false);
    jstring result = path;

    WIN32_FIND_DATA data;
    HANDLE h = FindFirstFileW(winPath, &data);
    if (h != INVALID_HANDLE_VALUE) {
        FindClose(h);

        if (IS_SET(data.dwFileAttributes, FILE_ATTRIBUTE_REPARSE_POINT) &&
            (IS_SET(data.dwReserved0, IO_REPARSE_TAG_SYMLINK) || IS_SET(data.dwReserved0, IO_REPARSE_TAG_MOUNT_POINT))) {
            HANDLE th = CreateFileW(winPath, 0, FILE_SHARE_ATTRIBUTES, NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, NULL);
            if (th != INVALID_HANDLE_VALUE) {
                wchar_t buff[MAX_PATH], * finalPath = buff;
                DWORD len = __GetFinalPathNameByHandle(th, buff, MAX_PATH, 0);
                if (len >= MAX_PATH) {
                    finalPath = (wchar_t*)malloc((len + 1) * sizeof(wchar_t));
                    len = finalPath != NULL ? __GetFinalPathNameByHandle(th, finalPath, len, 0) : 0;
                }
                if (len > 0) {
                    int prefix = (len > 4 && finalPath[0] == L'\\' && finalPath[1] == L'\\' && finalPath[2] == L'?' && finalPath[3] == L'\\') ? 4 : 0;
                    result = env->NewString((jchar*)finalPath + prefix, len - prefix);
                    if (finalPath != buff) {
                        free(finalPath);
                    }
                }
                CloseHandle(th);
            }
            else {
                result = NULL;
            }
        }
    }
    else {
        result = NULL;
    }

    free(winPath);
    return result;
}

JNIEXPORT jobjectArray JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_listChildren0(JNIEnv* env, jobject method, jstring path) {
    jclass fileInfoClass = env->FindClass(FILE_INFO_CLASS);
    if (fileInfoClass == NULL) {
        return NULL;
    }

    wchar_t* winPath = ToWinPath(env, path, true);
    if (winPath == NULL) {
        return NULL;
    }

    WIN32_FIND_DATA data;
    HANDLE h = FindFirstFileW(winPath, &data);
    if (h == INVALID_HANDLE_VALUE) {
        free(winPath);
        return NULL;
    }

    jsize len = 0, maxLen = 16;
    jobjectArray result = env->NewObjectArray(maxLen, fileInfoClass, NULL);
    if (result != NULL) {
        do {
            if (wcscmp(data.cFileName, L".") == 0 || wcscmp(data.cFileName, L"..") == 0) {
                continue;
            }

            if (len == maxLen) {
                result = CopyObjectArray(env, result, fileInfoClass, len, maxLen <<= 1);
                if (result == NULL) {
                    goto exit;
                }
            }

            jobject o = CreateFileInfo(env, winPath, true, &data, fileInfoClass);
            env->SetObjectArrayElement(result, len++, o);
            env->DeleteLocalRef(o);
        }
        while (FindNextFile(h, &data));

        if (len != maxLen) {
            result = CopyObjectArray(env, result, fileInfoClass, len, len);
        }
    }

exit:
    free(winPath);
    FindClose(h);
    return result;
}

BOOL APIENTRY DllMain(HMODULE hModule, DWORD ul_reason_for_call, LPVOID lpReserved) {
    return TRUE;
}


// utility methods

static inline LONGLONG pairToInt64(DWORD lowPart, DWORD highPart);

static wchar_t* ToWinPath(JNIEnv* env, jstring path, bool dirSuffix) {
    jsize len = env->GetStringLength(path), prefix = 0, suffix = 0;
    const jchar* jstr = env->GetStringChars(path, NULL);
    while (len > 0 && jstr[len - 1] == L'\\') --len;  // trim trailing separators
    if (len == 0) return NULL;
    if (len >= MAX_PATH) prefix = 4;  // prefix long paths by UNC marker
    if (dirSuffix) suffix = 2;

    wchar_t* pathBuf = (wchar_t*)malloc((prefix + len + suffix + 1) * sizeof(wchar_t));
    if (pathBuf != NULL) {
        if (prefix > 0) {
            wcsncpy_s(pathBuf, prefix + 1, L"\\\\?\\", prefix);
        }
        wcsncpy_s(pathBuf + prefix, len + 1, (wchar_t*)jstr, len);
        if (suffix > 0) {
            wcsncpy_s(pathBuf + prefix + len, suffix + 1, L"\\*", suffix);
        }
        pathBuf[prefix + len + suffix] = L'\0';
    }

    env->ReleaseStringChars(path, jstr);

    return pathBuf;
}

static jobject CreateFileInfo(JNIEnv* env, wchar_t* path, bool isDirectory, LPWIN32_FIND_DATA lpData, jclass aClass) {
    DWORD attributes = lpData->dwFileAttributes;
    LONGLONG timestamp = pairToInt64(lpData->ftLastWriteTime.dwLowDateTime, lpData->ftLastWriteTime.dwHighDateTime);
    LONGLONG length = pairToInt64(lpData->nFileSizeLow, lpData->nFileSizeHigh);

    if (IS_SET(attributes, FILE_ATTRIBUTE_REPARSE_POINT)) {
        if (IS_SET(lpData->dwReserved0, IO_REPARSE_TAG_SYMLINK)) {
            attributes = BROKEN_SYMLINK_ATTR;
            timestamp = 0;
            length = 0;

            wchar_t* fullPath = path;
            if (isDirectory) {
                // trim '*' and append file name
                size_t dirLen = wcslen(path) - 1, nameLen = wcslen(lpData->cFileName), fullLen = dirLen + nameLen + 1;
                fullPath = (wchar_t*)malloc(fullLen * sizeof(wchar_t));
                if (fullPath == NULL) {
                    return NULL;
                }
                wcsncpy_s(fullPath, dirLen + 1, path, dirLen);
                wcsncpy_s(fullPath + dirLen, nameLen + 1, lpData->cFileName, nameLen);
            }

            // read symlink target attributes
            HANDLE h = CreateFileW(fullPath, 0, FILE_SHARE_ATTRIBUTES, NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, NULL);
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
        }
        else {
            attributes &= (~ FILE_ATTRIBUTE_REPARSE_POINT);  // keep reparse flag only for symlinks
        }
    }

    jobject o = env->AllocObject(aClass);
    if (o == NULL) {
        return NULL;
    }

    jstring fileName = env->NewString((jchar*)lpData->cFileName, (jsize)wcslen(lpData->cFileName));
    if (fileName == NULL) {
        return NULL;
    }

    env->SetObjectField(o, nameID, fileName);
    env->SetIntField(o, attributesID, attributes);
    env->SetLongField(o, timestampID, timestamp);
    env->SetLongField(o, lengthID, length);

    return o;
}

static jobjectArray CopyObjectArray(JNIEnv* env, jobjectArray src, jclass aClass, jsize count, jsize newSize) {
    jobjectArray dst = env->NewObjectArray(newSize, aClass, NULL);
    if (dst != NULL) {
        for (jsize i = 0; i < count; i++) {
            jobject o = env->GetObjectArrayElement(src, i);
            env->SetObjectArrayElement(dst, i, o);
            env->DeleteLocalRef(o);
        }
    }
    env->DeleteLocalRef(src);
    return dst;
}

static inline LONGLONG pairToInt64(DWORD lowPart, DWORD highPart) {
    ULARGE_INTEGER large;
    large.LowPart = lowPart;
    large.HighPart = highPart;
    return large.QuadPart;
}
