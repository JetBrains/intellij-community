/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 16, 2002
 * Time: 7:14:37 PM
 * To change template for new interface use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.projectRoots.ex;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface ProjectRootContainer {
  @NotNull
  VirtualFile[] getRootFiles(OrderRootType type);
  ProjectRoot[] getRoots(OrderRootType type);

  void startChange();
  void finishChange();

  ProjectRoot addRoot(VirtualFile virtualFile, OrderRootType type);
  void addRoot(ProjectRoot root, OrderRootType type);
  void removeRoot(ProjectRoot root, OrderRootType type);
  void removeAllRoots(OrderRootType type);

  void removeAllRoots();

  void removeRoot(VirtualFile root, OrderRootType type);

  void update();
}
