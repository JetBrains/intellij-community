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

#define BROKEN_SYMLINK_ATTR -1

static jclass getFileInfoClass(JNIEnv *env) {
    return env->FindClass("com/intellij/openapi/util/io/win32/FileInfo");
}

static bool CopyObjectArray(JNIEnv *env, jobjectArray dst, jobjectArray src, jint count) {
    for (int i = 0; i < count; i++) {
        jobject p = env->GetObjectArrayElement(src, i);
        env->SetObjectArrayElement(dst, i, p);
        env->DeleteLocalRef(p);
    }
    return true;
}

static inline LONGLONG pairToInt64(DWORD lowPart, DWORD highPart) {
    ULARGE_INTEGER large;
    large.LowPart = lowPart;
    large.HighPart = highPart;
    return large.QuadPart;
}

#define IS_SET(flags, flag) ((flags & flag) == flag)
#define FILE_SHARE_ATTRIBUTES (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)

static jobject CreateFileInfo(JNIEnv *env, jstring path, bool append, LPWIN32_FIND_DATA lpData, jclass fileInfoClass) {
    DWORD attributes = lpData->dwFileAttributes;
    LONGLONG timestamp = pairToInt64(lpData->ftLastWriteTime.dwLowDateTime, lpData->ftLastWriteTime.dwHighDateTime);
    LONGLONG length = pairToInt64(lpData->nFileSizeLow, lpData->nFileSizeHigh);

    if (IS_SET(attributes, FILE_ATTRIBUTE_REPARSE_POINT)) {
        if (IS_SET(lpData->dwReserved0, IO_REPARSE_TAG_SYMLINK)) {
            attributes = BROKEN_SYMLINK_ATTR;
            timestamp = 0;
            length = 0;

            const jchar *dirName = env->GetStringChars(path, 0);
            wchar_t *fullPath = (wchar_t *)dirName;

            if (append) {
                size_t nameLen = env->GetStringLength(path) + wcslen(lpData->cFileName) + 2;
                fullPath = (wchar_t *)malloc(nameLen * sizeof(wchar_t));
                if (fullPath != NULL) {
                    wcscpy_s(fullPath, nameLen, (LPCWSTR)dirName);
                    wcscat_s(fullPath, nameLen, L"\\");
                    wcscat_s(fullPath, nameLen, lpData->cFileName);
                }
            }

            if (fullPath != NULL) {
                // read symlink target attributes
                HANDLE h = CreateFile(fullPath, 0, FILE_SHARE_ATTRIBUTES, NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, NULL);
                if (h != INVALID_HANDLE_VALUE) {
                    BY_HANDLE_FILE_INFORMATION targetData;
                    if (GetFileInformationByHandle(h, &targetData)) {
                        attributes = targetData.dwFileAttributes | FILE_ATTRIBUTE_REPARSE_POINT;
                        timestamp = pairToInt64(targetData.ftLastWriteTime.dwLowDateTime, targetData.ftLastWriteTime.dwHighDateTime);
                        length = pairToInt64(targetData.nFileSizeLow, targetData.nFileSizeHigh);
                    }
                    CloseHandle(h);
                }

                if (append) {
                    free(fullPath);
                }
            }

            env->ReleaseStringChars(path, dirName);
        }
        else {
            attributes &= (~ FILE_ATTRIBUTE_REPARSE_POINT);  // keep reparse flag only for symlinks
        }
    }

    jobject o = env->AllocObject(fileInfoClass);
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


JNIEXPORT void JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_initIDs(JNIEnv *env, jclass cls) {
    __GetFinalPathNameByHandle =
        (GetFinalPathNameByHandlePtr)GetProcAddress(GetModuleHandle(L"kernel32.dll"), "GetFinalPathNameByHandleW");

    jclass fileInfoClass = getFileInfoClass(env);
    if (fileInfoClass == NULL) {
        return;
    }

    nameID = env->GetFieldID(fileInfoClass, "name", "Ljava/lang/String;");
    attributesID = env->GetFieldID(fileInfoClass, "attributes", "I");
    timestampID = env->GetFieldID(fileInfoClass, "timestamp", "J");
    lengthID = env->GetFieldID(fileInfoClass, "length", "J");
}


JNIEXPORT jobject JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_getInfo0(JNIEnv *env, jobject method, jstring path) {
    const jchar* pathStr = env->GetStringChars(path, 0);

    WIN32_FILE_ATTRIBUTE_DATA attrData;
    BOOL res = GetFileAttributesEx((LPCWSTR)pathStr, GetFileExInfoStandard, &attrData);
    if (!res) {
        env->ReleaseStringChars(path, pathStr);
        return NULL;
    }

    WIN32_FIND_DATA data;
    data.dwFileAttributes = attrData.dwFileAttributes;
    data.dwReserved0 = 0;
    data.ftLastWriteTime = attrData.ftLastWriteTime;
    data.nFileSizeLow = attrData.nFileSizeLow;
    data.nFileSizeHigh = attrData.nFileSizeHigh;

    if (IS_SET(attrData.dwFileAttributes, FILE_ATTRIBUTE_REPARSE_POINT)) {
        WIN32_FIND_DATA findData;
        HANDLE h = FindFirstFile((LPCWSTR)pathStr, &findData);
        if (h != INVALID_HANDLE_VALUE) {
            FindClose(h);
            data.dwFileAttributes = findData.dwFileAttributes;
            data.dwReserved0 = findData.dwReserved0;
            data.ftLastWriteTime = findData.ftLastWriteTime;
            data.nFileSizeLow = findData.nFileSizeLow;
            data.nFileSizeHigh = findData.nFileSizeHigh;
        }
    }

    env->ReleaseStringChars(path, pathStr);

    jclass fileInfoClass = getFileInfoClass(env);
    if (fileInfoClass == NULL) {
        return NULL;
    }

    return CreateFileInfo(env, path, false, &data, fileInfoClass);
}


JNIEXPORT jstring JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_resolveSymLink0(JNIEnv *env, jobject method, jstring path) {
    if (__GetFinalPathNameByHandle == NULL) {
        return NULL;  // XP
    }

    const jchar* pathStr = env->GetStringChars(path, 0);
    jstring result = NULL;

    WIN32_FIND_DATA data;
    HANDLE h = FindFirstFile((LPCWSTR)pathStr, &data);
    if (h != INVALID_HANDLE_VALUE) {
        FindClose(h);

        if (IS_SET(data.dwFileAttributes, FILE_ATTRIBUTE_REPARSE_POINT) && IS_SET(data.dwReserved0, IO_REPARSE_TAG_SYMLINK)) {
            HANDLE th = CreateFile((LPCWSTR)pathStr, 0, FILE_SHARE_ATTRIBUTES, NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, NULL);
            if (th != INVALID_HANDLE_VALUE) {
                TCHAR name[MAX_PATH];
                DWORD len = __GetFinalPathNameByHandle(th, name, MAX_PATH, 0);
                if (len > 0) {
                    if (len < MAX_PATH) {
                        result = env->NewString((jchar *)name, len);
                    }
                    else {
                        TCHAR *name = (TCHAR *)malloc(sizeof(TCHAR) * (len + 1));
                        if (name != NULL) {
                            len = __GetFinalPathNameByHandle(th, name, len, 0);
                            if (len > 0) {
                                result = env->NewString((jchar *)name, len);
                            }
                            free(name);
                        }
                    }
                }
                CloseHandle(th);

            }
        }
    }

    env->ReleaseStringChars(path, pathStr);
    return result;
}


JNIEXPORT jobjectArray JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_listChildren0(JNIEnv *env, jobject method, jstring path) {
    jclass fileInfoClass = getFileInfoClass(env);
    if (fileInfoClass == NULL) {
        return NULL;
    }

    size_t pathLen = env->GetStringLength(path) + 3;
    wchar_t* pathStr = (wchar_t *)malloc(pathLen * sizeof(wchar_t));
    if (pathStr == NULL) {
        return NULL;
    }
    const jchar* str = env->GetStringChars(path, 0);
    wcscpy_s(pathStr, pathLen, (LPCWSTR)str);
    wcscat_s(pathStr, pathLen, L"\\*");
    env->ReleaseStringChars(path, str);

    WIN32_FIND_DATA data;
    HANDLE h = FindFirstFile((LPCWSTR)pathStr, &data);
    if (h == INVALID_HANDLE_VALUE) {
        free(pathStr);
        return NULL;
    }

    jobjectArray rv, old;
    int len = 0, maxlen = 16;
    rv = env->NewObjectArray(maxlen, fileInfoClass, NULL);
    if (rv == NULL) {
        goto error;
    }

    do {
        if (len == maxlen) {
            old = rv;
            rv = env->NewObjectArray(maxlen <<= 1, fileInfoClass, NULL);
            if (rv == NULL || !CopyObjectArray(env, rv, old, len)) {
                goto error;
            }
            env->DeleteLocalRef(old);
        }
        jobject o = CreateFileInfo(env, path, true, &data, fileInfoClass);
        env->SetObjectArrayElement(rv, len++, o);
        env->DeleteLocalRef(o);
    }
    while (FindNextFile(h, &data));

    free(pathStr);
    FindClose(h);

    old = rv;
    rv = env->NewObjectArray(len, fileInfoClass, NULL);
    if (rv == NULL || !CopyObjectArray(env, rv, old, len)) {
        goto error;
    }

    return rv;

error:
    free(pathStr);
    FindClose(h);
    return NULL;
}


BOOL APIENTRY DllMain(HMODULE hModule, DWORD ul_reason_for_call, LPVOID lpReserved) {
    return TRUE;
}
