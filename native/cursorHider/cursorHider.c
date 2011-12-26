#include <jni.h>
#include <ApplicationServices/ApplicationServices.h>

JNIEXPORT void JNICALL Java_com_intellij_util_ui_MacUIUtil_doHideCursor
  (JNIEnv *env, jclass clazz) {
  CGDisplayHideCursor(kCGDirectMainDisplay);
}

JNIEXPORT void JNICALL Java_com_intellij_util_ui_MacUIUtil_doShowCursor
  (JNIEnv *env, jclass clazz) {
  CGDisplayShowCursor(kCGDirectMainDisplay);
}

