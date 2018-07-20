// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include <jni.h>

#ifndef _Included_com_intellij_openapi_util_io_win32_IdeaWin32
#define _Included_com_intellij_openapi_util_io_win32_IdeaWin32
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_initIDs(JNIEnv *, jclass);

JNIEXPORT jobject JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_getInfo0(JNIEnv *, jobject, jstring);

JNIEXPORT jstring JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_resolveSymLink0(JNIEnv *, jobject, jstring);

JNIEXPORT jobjectArray JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_listChildren0(JNIEnv *, jobject, jstring);

#ifdef __cplusplus
}
#endif
#endif