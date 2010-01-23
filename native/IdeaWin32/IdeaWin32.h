#include "jni.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL Java_com_intellij_openapi_vfs_impl_win32_FileInfo_initIDs(JNIEnv *env, jclass cls);

JNIEXPORT jobject JNICALL Java_com_intellij_openapi_vfs_impl_win32_IdeaWin32_getInfo(JNIEnv *env, jobject method, jstring path);

JNIEXPORT jobjectArray JNICALL Java_com_intellij_openapi_vfs_impl_win32_IdeaWin32_listChildren(JNIEnv *env, jobject method, jstring path);

#ifdef __cplusplus
}
#endif
