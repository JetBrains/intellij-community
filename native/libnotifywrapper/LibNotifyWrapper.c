#include "com_intellij_ui_LibNotifyWrapper.h"

// to build 32bit version use
// gcc -I/usr/include/gdk-pixbuf-2.0 -I/usr/lib/i386-linux-gnu/glib-2.0/include/ -I/usr/include/glib-2.0/ -I/jdk/jdk1.6.0_45/include -I/jdk/jdk1.6.0_45/include/linux  -fPIC -shared  -o libnotifywrapper.so LibNotifyWrapper.c -L/usr/lib/i386-linux-gnu/ -m32
// do not forget something like "sudo apt-get install libnotify-dev:i386"

// to build 64 bit version use
// gcc `pkg-config --cflags --libs libnotify` -I/jdk/jdk1.6.0_45/include -I/jdk/jdk1.6.0_45/include/linux  -fPIC -shared  -o libnotifywrapper64.so LibNotifyWrapper.c -lnotify

/*
 * Class:     com_intellij_ui_LibNotifyWrapper
 * Method:    showNotification
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_intellij_ui_LibNotifyWrapper_showNotification
  (JNIEnv * jEnv, jclass c, jstring jTitle, jstring jDescription) {
  const char *title = (*jEnv)->GetStringUTFChars(jEnv, jTitle, 0);
  const char *description = (*jEnv)->GetStringUTFChars(jEnv, jDescription, 0);
  NotifyNotification * notification = notify_notification_new (title, description, "idea.png");
  notify_init ("JetBrains");
  notify_notification_show (notification, NULL);
  notify_uninit();
  (*jEnv)->ReleaseStringUTFChars(jEnv, jTitle, title);
  (*jEnv)->ReleaseStringUTFChars(jEnv, jDescription, description);
}

