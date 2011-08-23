#include "stdafx.h"
#include "IdeaWin32.h"
#include "jni_util.h"
#include "windows.h"

jfieldID nameID;
jfieldID attributesID;
jfieldID timestampID;
jfieldID lengthID;

JNIEXPORT void JNICALL Java_com_intellij_openapi_vfs_impl_win32_FileInfo_initIDs(JNIEnv *env, jclass cls) {
    nameID = env->GetFieldID(cls, "name", "Ljava/lang/String;");
    attributesID = env->GetFieldID(cls, "attributes", "I");
    timestampID = env->GetFieldID(cls, "timestamp", "J");
    lengthID = env->GetFieldID(cls, "length", "J");
}

static HANDLE FindFileInner(JNIEnv *env, jstring path, LPWIN32_FIND_DATA lpData) {
    const jchar* str = env->GetStringChars(path, 0);
    const HANDLE h = FindFirstFile((LPCWSTR)str, lpData);
    env->ReleaseStringChars(path, str);
    return h;
}

static bool CopyObjectArray(JNIEnv *env, jobjectArray dst, jobjectArray src, jint count) {
    for (int i = 0; i < count; i++) {
        jobject p = env->GetObjectArrayElement(src, i);
        env->SetObjectArrayElement(dst, i, p);
        env->DeleteLocalRef(p);
    }
    return true;
}

static LONGLONG pairToInt64(DWORD lowPart, DWORD highPart) {
    ULARGE_INTEGER large;
    large.LowPart = lowPart;
    large.HighPart = highPart;
    return large.QuadPart;
}

#define IS_SET(flags, flag) ((flags & flag) == flag)

static jobject CreateFileInfo(JNIEnv *env, jstring path, bool append, LPWIN32_FIND_DATA lpData, jclass cls) {
    jobject o = env->AllocObject(cls);
    if (o == NULL) {
        return NULL;
    }

    jstring fileName = env->NewString((jchar*)lpData->cFileName, (jsize)wcslen(lpData->cFileName));
    if (fileName == NULL) {
        return NULL;
    }
    env->SetObjectField(o, nameID, fileName);

    bool read = false;
    if (IS_SET(lpData->dwFileAttributes, FILE_ATTRIBUTE_REPARSE_POINT) && IS_SET(lpData->dwReserved0, IO_REPARSE_TAG_SYMLINK)) {
        int nameLen = env->GetStringLength(path) + wcslen(lpData->cFileName) + 2;
        wchar_t *lpName = (wchar_t *)malloc(nameLen * sizeof(wchar_t));

        if (lpName != NULL) {
            const jchar *dirName = env->GetStringChars(path, 0);
            wcscpy_s(lpName, nameLen, (LPCWSTR)dirName);
            env->ReleaseStringChars(path, dirName);
            if (append) {
                wcscat_s(lpName, nameLen, L"\\");
                wcscat_s(lpName, nameLen, lpData->cFileName);
            }

            // read symlink target attributes
            HANDLE th = CreateFile(lpName, FILE_READ_ATTRIBUTES, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE, NULL,
                                   OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, NULL);
            if (th != INVALID_HANDLE_VALUE) {
                BY_HANDLE_FILE_INFORMATION targetData;
                if (GetFileInformationByHandle(th, &targetData)) {
                    env->SetIntField(o, attributesID, targetData.dwFileAttributes);
                    env->SetLongField(o, timestampID, pairToInt64(targetData.ftLastWriteTime.dwLowDateTime, targetData.ftLastWriteTime.dwHighDateTime));
                    env->SetLongField(o, lengthID, pairToInt64(targetData.nFileSizeLow, targetData.nFileSizeHigh));
                    read = true;
                }
                CloseHandle(th);
            }

            free(lpName);
        }
    }

    if (!read) {
        env->SetIntField(o, attributesID, lpData->dwFileAttributes);
        env->SetLongField(o, timestampID, pairToInt64(lpData->ftLastWriteTime.dwLowDateTime, lpData->ftLastWriteTime.dwHighDateTime));
        env->SetLongField(o, lengthID, pairToInt64(lpData->nFileSizeLow, lpData->nFileSizeHigh));
    }

    return o;
}

JNIEXPORT jobject JNICALL Java_com_intellij_openapi_vfs_impl_win32_IdeaWin32_getInfo(JNIEnv *env, jobject method, jstring path) {
    WIN32_FIND_DATA data;
    HANDLE h = FindFileInner(env, path, &data);
    if (h == INVALID_HANDLE_VALUE) {
        return NULL;
    }
    FindClose(h);	
    jclass infoClass = env->FindClass("com/intellij/openapi/vfs/impl/win32/FileInfo");
    return CreateFileInfo(env, path, false, &data, infoClass);
}

JNIEXPORT jobjectArray JNICALL Java_com_intellij_openapi_vfs_impl_win32_IdeaWin32_listChildren(JNIEnv *env, jobject method, jstring path) {
    WIN32_FIND_DATA data;
    HANDLE h = FindFileInner(env, path, &data);
    if (h == INVALID_HANDLE_VALUE) {
        return NULL;
    }

    jobjectArray rv, old;
    int len = 0, maxlen = 16;
    jclass infoClass = env->FindClass("com/intellij/openapi/vfs/impl/win32/FileInfo");
    rv = env->NewObjectArray(maxlen, infoClass, NULL);
    if (rv == NULL) {
        goto error;
    }

    do {
        if (len == maxlen) {
            old = rv;
            rv = env->NewObjectArray(maxlen <<= 1, infoClass, NULL);
            if (rv == NULL || !CopyObjectArray(env, rv, old, len)) {
                goto error;
            }
            env->DeleteLocalRef(old);
        }
        jobject o = CreateFileInfo(env, path, true, &data, infoClass);
        env->SetObjectArrayElement(rv, len++, o);
        env->DeleteLocalRef(o);
    }
    while (FindNextFile(h, &data));

    FindClose(h);	

    old = rv;
    rv = env->NewObjectArray(len, infoClass, NULL);
    if (rv == NULL || !CopyObjectArray(env, rv, old, len)) {
        goto error;
    }
    return rv;

error:
    FindClose(h);	
    return NULL;
}

BOOL APIENTRY DllMain(HMODULE hModule, DWORD  ul_reason_for_call, LPVOID lpReserved) {
    return TRUE;
}
