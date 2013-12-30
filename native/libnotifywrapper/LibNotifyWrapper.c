#include "com_intellij_ui_LibNotifyWrapper.h"
#include <dlfcn.h>

/*
 * Class:     com_intellij_ui_LibNotifyWrapper
 * Method:    showNotification
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_intellij_ui_LibNotifyWrapper_showNotification
  (JNIEnv * jEnv, jclass c, jstring jTitle, jstring jDescription, jstring jLogoPath) {

  void *handle;
  gboolean (*notify_init_func)(const char *app_name);
  void (*notify_uninit_func)();
  NotifyNotification* (*notify_notification_new_func) (const char *summary,
                                                         const char *body,
                                                         const char *icon);
  gboolean (*notify_notification_show_func) (NotifyNotification *notification,
                                                         GError **error);
  char *error;

  dlerror();

  handle = dlopen("libnotify.so", RTLD_LAZY);

  if ((error = dlerror()) != NULL)  
  {
    (*jEnv)->ThrowNew(jEnv, (*jEnv)->FindClass(jEnv, "java/lang/UnsatisfiedLinkError"), error);
    return;
  }

  dlerror();
 
 *(void **) (&notify_init_func) = dlsym(handle, "notify_init");
 *(void **) (&notify_uninit_func) = dlsym(handle, "notify_init");
 *(void **) (&notify_notification_new_func) = dlsym(handle, "notify_notification_new");
 *(void **) (&notify_notification_show_func) = dlsym(handle, "notify_notification_show");

  (*notify_init_func) ("JetBrains");

  const char *title = (*jEnv)->GetStringUTFChars(jEnv, jTitle, 0);
  const char *description = (*jEnv)->GetStringUTFChars(jEnv, jDescription, 0);
  const char *logoPath = (*jEnv)->GetStringUTFChars(jEnv, jLogoPath, 0);
  NotifyNotification * notification = (*notify_notification_new_func) (title, description, logoPath);
  (*notify_notification_show_func) (notification, NULL);
  (*notify_uninit_func)();
  (*jEnv)->ReleaseStringUTFChars(jEnv, jTitle, title);
  (*jEnv)->ReleaseStringUTFChars(jEnv, jDescription, description);
  (*jEnv)->ReleaseStringUTFChars(jEnv, jLogoPath, logoPath);

  dlclose(handle);
}
