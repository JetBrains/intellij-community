/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 17, 2007
 * Time: 3:20:51 PM
 */
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class EncodingManager implements ApplicationComponent {
  public static EncodingManager getInstance() {
    return ServiceManager.getService(EncodingManager.class);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "EncodingManager";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  @Nullable
  public Charset getEncoding(@NotNull VirtualFile virtualFile, boolean useParentDefaults) {
    Project project = ProjectLocator.getInstance().guessProjectForFile(virtualFile);

    if (project == null) {
      ProjectManager projectManager = ProjectManager.getInstance();
      if (projectManager == null) return null; //tests
      project = projectManager.getDefaultProject();
    }
    return EncodingProjectManager.getInstance(project).getEncoding(virtualFile, useParentDefaults);
  }

  public void setEncoding(@Nullable VirtualFile virtualFileOrDir, @Nullable Charset charset) {
    Project project = ProjectLocator.getInstance().guessProjectForFile(virtualFileOrDir);
    EncodingProjectManager.getInstance(project).setEncoding(virtualFileOrDir, charset);
  }

  @NotNull
  public Collection<Charset> getFavorites() {
    Set<Charset> result = new THashSet<Charset>();
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      result.addAll(EncodingProjectManager.getInstance(project).getFavorites());
    }
    return result;
  }

  public void setMapping(final Map<VirtualFile, Charset> map) {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      EncodingProjectManager.getInstance(project).setMapping(map);
    }
  }

  public void restoreEncoding(final VirtualFile virtualFile, final Charset charsetBefore) {
    Charset actual = getEncoding(virtualFile, true);
    if (Comparing.equal(actual, charsetBefore)) return;
    setEncoding(virtualFile, charsetBefore);
  }
}
