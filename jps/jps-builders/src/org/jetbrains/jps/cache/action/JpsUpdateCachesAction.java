//package org.jetbrains.jps.cache.action;
//
//import com.intellij.jps.cache.client.JpsServerAuthExtension;
//import com.intellij.jps.cache.loader.JpsOutputLoaderManager;
//import com.intellij.openapi.actionSystem.AnActionEvent;
//import com.intellij.openapi.project.DumbAwareAction;
//import com.intellij.openapi.project.Project;
//import org.jetbrains.annotations.NotNull;
//
//public class JpsUpdateCachesAction extends DumbAwareAction {
//  @Override
//  public void actionPerformed(@NotNull AnActionEvent actionEvent) {
//    Project project = actionEvent.getProject();
//    if (project == null) return;
//    JpsOutputLoaderManager outputLoaderManager = JpsOutputLoaderManager.getInstance(project);
//    JpsServerAuthExtension.checkAuthenticatedInBackgroundThread(outputLoaderManager, project, () -> outputLoaderManager.load(false, true));
//  }
//}