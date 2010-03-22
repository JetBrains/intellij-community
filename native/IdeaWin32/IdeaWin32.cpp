#include "stdafx.h"
#include "IdeaWin32.h"
#include "jni_util.h"
#include "windows.h"

jfieldID nameId;

HANDLE FindFileInner(JNIEnv *env, jstring path, LPWIN32_FIND_DATA lpData) {
	const jchar* str = env->GetStringChars(path, 0);
	HANDLE h = FindFirstFile((LPCWSTR)str, lpData);
	env->ReleaseStringChars(path, str);
	return h;
}

bool CopyObjectArray(JNIEnv *env, jobjectArray dst, jobjectArray src,
                         jint count)
{
    int i;
    for (i=0; i<count; i++) {
        jobject p = env->GetObjectArrayElement(src, i);
        env->SetObjectArrayElement(dst, i, p);
        env->DeleteLocalRef(p);
    }
    return true;
}

static LONGLONG
fileTimeToInt64 (const FILETIME * time)
{
    ULARGE_INTEGER _time;

    _time.LowPart = time->dwLowDateTime;
    _time.HighPart = time->dwHighDateTime;

    return _time.QuadPart;
}

jfieldID nameID; 
jfieldID attributesID;
jfieldID timestampID;

jobject CreateFileInfo(JNIEnv *env, LPWIN32_FIND_DATA lpData, jclass cls) {

	jobject o = env->AllocObject(cls);
	if (o == NULL) {
		return NULL;
	}
	jstring fileName = env->NewString((jchar*)lpData->cFileName, (jsize)wcslen(lpData->cFileName));
	if (fileName == NULL) {
		return NULL;
	}
	env->SetObjectField(o, nameID, fileName);
	env->SetIntField(o, attributesID, lpData->dwFileAttributes);
	env->SetLongField(o, timestampID, fileTimeToInt64(&lpData->ftLastWriteTime));
	return o;
}

JNIEXPORT void JNICALL Java_com_intellij_openapi_vfs_impl_win32_FileInfo_initIDs(JNIEnv *env, jclass cls) {
	nameID = env->GetFieldID(cls, "name", "Ljava/lang/String;");
	attributesID = env->GetFieldID(cls, "attributes", "I");
	timestampID = env->GetFieldID(cls, "timestamp", "J");
}

JNIEXPORT jobject JNICALL Java_com_intellij_openapi_vfs_impl_win32_IdeaWin32_getInfo(JNIEnv *env, jobject method, jstring path) {
	WIN32_FIND_DATA data;
	HANDLE h = FindFileInner(env, path, &data);
	if (h == INVALID_HANDLE_VALUE) {
	  return NULL;
	}
	FindClose(h);	
	jclass infoClass = env->FindClass("com/intellij/openapi/vfs/impl/win32/FileInfo");
	return CreateFileInfo(env, &data, infoClass);
}

JNIEXPORT jboolean JNICALL Java_com_intellij_openapi_vfs_impl_win32_IdeaWin32_checkExist(JNIEnv *env, jobject method, jstring path) {
	jint result = 0;
    WIN32_FILE_ATTRIBUTE_DATA wfad;
	const jchar* str = env->GetStringChars(path, 0);

    if (GetFileAttributesEx((LPCWSTR)path, GetFileExInfoStandard, &wfad)) return JNI_TRUE;
	return JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL Java_com_intellij_openapi_vfs_impl_win32_IdeaWin32_listChildren(JNIEnv *env, jobject method, jstring path)
{

  WIN32_FIND_DATA data;
  HANDLE h = FindFileInner(env, path, &data);
  if (h == INVALID_HANDLE_VALUE) {
	  return NULL;
  }

  jobjectArray rv, old;
  int len, maxlen;

  len = 0;
  maxlen = 16;
  jclass infoClass = env->FindClass("com/intellij/openapi/vfs/impl/win32/FileInfo");
  rv = env->NewObjectArray(maxlen, infoClass, NULL);
  if (rv == NULL) goto error;
  do  {
        if (len == maxlen) {
            old = rv;
            rv = env->NewObjectArray(maxlen <<= 1,
                                        infoClass, NULL);
            if (rv == NULL) goto error;
            if (!CopyObjectArray(env, rv, old, len)) goto error;
            env->DeleteLocalRef(old);
        }
		jobject o = CreateFileInfo(env, &data, infoClass);
	  env->SetObjectArrayElement(rv, len++, o);
	  env->DeleteLocalRef(o);
  }
  while (FindNextFile(h, &data));

  FindClose(h);	

	old = rv;
	rv = env->NewObjectArray(len, infoClass, NULL);
	if (rv == NULL) goto error;
	if (!CopyObjectArray(env, rv, old, len)) goto error;
	return rv;


error:
  FindClose(h);	
  return NULL;
}

BOOL APIENTRY DllMain( HMODULE hModule,
                       DWORD  ul_reason_for_call,
                       LPVOID lpReserved
					 )
{
    return TRUE;
}

