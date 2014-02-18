#include "com_intellij_ui_LibNotifyWrapper.h"
#include <dlfcn.h>

static void *libnotify_handle;
static gboolean (*notify_init_func)(const char *app_name);
static void (*notify_uninit_func)();
static NotifyNotification* (*notify_notification_new_func) (const char *summary,
                                                            const char *body,
                                                            const char *icon);
static gboolean (*notify_notification_show_func) (NotifyNotification *notification,
                                                  GError **error);

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
  if (libnotify_handle != NULL) {
    (*notify_uninit_func)();
    dlclose(libnotify_handle);
  }
}

/*
 * Class:     com_intellij_ui_LibNotifyWrapper
 * Method:    showNotification
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_intellij_ui_LibNotifyWrapper_showNotification
  (JNIEnv * jEnv, jclass c, jstring jTitle, jstring jDescription, jstring jLogoPath) {

  if (libnotify_handle == NULL) {
    char *error;

    dlerror();

    libnotify_handle = dlopen("libnotify.so", RTLD_LAZY);

    if ((error = dlerror()) != NULL)
    {
      (*jEnv)->ThrowNew(jEnv, (*jEnv)->FindClass(jEnv, "java/lang/UnsatisfiedLinkError"), error);
      return;
    }

    dlerror();

    *(void **) (&notify_init_func) = dlsym(libnotify_handle, "notify_init");
    *(void **) (&notify_uninit_func) = dlsym(libnotify_handle, "notify_uninit");
    *(void **) (&notify_notification_new_func) = dlsym(libnotify_handle, "notify_notification_new");
    *(void **) (&notify_notification_show_func) = dlsym(libnotify_handle, "notify_notification_show");

    (*notify_init_func) ("JetBrains");
  }

  const char *title = (*jEnv)->GetStringUTFChars(jEnv, jTitle, 0);
  const char *description = (*jEnv)->GetStringUTFChars(jEnv, jDescription, 0);
  const char *logoPath = (*jEnv)->GetStringUTFChars(jEnv, jLogoPath, 0);
  NotifyNotification * notification = (*notify_notification_new_func) (title, description, logoPath);
  (*notify_notification_show_func) (notification, NULL);
  (*jEnv)->ReleaseStringUTFChars(jEnv, jTitle, title);
  (*jEnv)->ReleaseStringUTFChars(jEnv, jDescription, description);
  (*jEnv)->ReleaseStringUTFChars(jEnv, jLogoPath, logoPath);
}
