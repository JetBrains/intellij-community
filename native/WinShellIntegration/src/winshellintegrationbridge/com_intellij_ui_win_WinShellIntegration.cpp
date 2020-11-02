#include "com_intellij_ui_win_WinShellIntegration.h"
#include "win_shell_integration_bridge.h"


/*
 * Class:     com_intellij_ui_win_WinShellIntegration
 * Method:    initializeNative
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_intellij_ui_win_WinShellIntegration_initializeNative(
    JNIEnv* jEnv,
    jclass jClass,
    jstring jAppUserModelId)
{
    return (void)intellij::ui::win::jni::WinShellIntegrationBridge::initialize(jEnv, jClass, jAppUserModelId);
}

/*
 * Class:     com_intellij_ui_win_WinShellIntegration
 * Method:    clearRecentTasksListNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_intellij_ui_win_WinShellIntegration_clearRecentTasksListNative(
    JNIEnv* jEnv,
    jclass jClass)
{
    return (void)intellij::ui::win::jni::WinShellIntegrationBridge::clearRecentTasksList(jEnv, jClass);
}

/*
 * Class:     com_intellij_ui_win_WinShellIntegration
 * Method:    setRecentTasksListNative
 * Signature: ([Lcom/intellij/ui/win/Task;)V
 */
JNIEXPORT void JNICALL Java_com_intellij_ui_win_WinShellIntegration_setRecentTasksListNative(
    JNIEnv* jEnv,
    jclass jClass,
    jobjectArray jTasks)
{
    return (void)intellij::ui::win::jni::WinShellIntegrationBridge::setRecentTasksList(jEnv, jClass, jTasks);
}
