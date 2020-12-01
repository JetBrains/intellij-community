#include "com_intellij_ui_win_WinShellIntegration_Bridge.h"
#include "win_shell_integration_bridge.h"


/*
 * Class:     com_intellij_ui_win_WinShellIntegration
 * Method:    setAppUserModelIdNative
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_intellij_ui_win_WinShellIntegration_00024Bridge_setAppUserModelIdNative(
    JNIEnv* jEnv,
    jobject jThis,
    jstring jAppUserModelId)
{
    return (void)intellij::ui::win::jni::WinShellIntegrationBridge::setAppUserModelId(jEnv, jThis, jAppUserModelId);
}


/*
 * Class:     com_intellij_ui_win_WinShellIntegration
 * Method:    initializeNative
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_intellij_ui_win_WinShellIntegration_00024Bridge_initializeNative(
    JNIEnv* jEnv,
    jobject jThis)
{
    return (void)intellij::ui::win::jni::WinShellIntegrationBridge::initialize(jEnv, jThis);
}

/*
 * Class:     com_intellij_ui_win_WinShellIntegration
 * Method:    clearRecentTasksListNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_intellij_ui_win_WinShellIntegration_00024Bridge_clearRecentTasksListNative(
    JNIEnv* jEnv,
    jobject jThis)
{
    return (void)intellij::ui::win::jni::WinShellIntegrationBridge::clearRecentTasksList(jEnv, jThis);
}

/*
 * Class:     com_intellij_ui_win_WinShellIntegration
 * Method:    setRecentTasksListNative
 * Signature: ([Lcom/intellij/ui/win/Task;)V
 */
JNIEXPORT void JNICALL Java_com_intellij_ui_win_WinShellIntegration_00024Bridge_setRecentTasksListNative(
    JNIEnv* jEnv,
    jobject jThis,
    jobjectArray jTasks)
{
    return (void)intellij::ui::win::jni::WinShellIntegrationBridge::setRecentTasksList(jEnv, jThis, jTasks);
}
