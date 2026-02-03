// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.scratch;

import com.intellij.debugger.NoDataException;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.PositionManagerImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class JavaScratchPositionManager extends PositionManagerImpl{
  private final @NotNull VirtualFile myScratchFile;

  public JavaScratchPositionManager(@NotNull DebugProcessImpl debugProcess, @NotNull VirtualFile scratchFile) {
    super(debugProcess);
    myScratchFile = scratchFile;
  }

  @Override
  public @NotNull List<Location> locationsOfLine(@NotNull ReferenceType type, @NotNull SourcePosition position) throws NoDataException {
    checkPosition(position);
    return super.locationsOfLine(type, position);
  }

  @Override
  public @NotNull List<ClassPrepareRequest> createPrepareRequests(@NotNull ClassPrepareRequestor requestor,
                                                                  @NotNull SourcePosition position) throws NoDataException {
    checkPosition(position);
    return super.createPrepareRequests(requestor, position);
  }

  @Override
  public @NotNull @Unmodifiable List<ReferenceType> getAllClasses(@NotNull SourcePosition position) throws NoDataException {
    checkPosition(position);
    return super.getAllClasses(position);
  }

  private void checkPosition(@NotNull SourcePosition position) throws NoDataException{
    if (!myScratchFile.equals(position.getFile().getVirtualFile())) {
      throw NoDataException.INSTANCE;
    }
  }

  @Override
  public @Nullable SourcePosition getSourcePosition(Location location) throws NoDataException {
    final SourcePosition position = super.getSourcePosition(location);
    if (position == null) {
      throw NoDataException.INSTANCE; // delegate to other managers
    }
    return position;
  }

  @Override
  protected @Nullable PsiFile getPsiFileByLocation(Project project, Location location) {
    if (location == null || !myScratchFile.isValid()) {
      return null;
    }
    final ReferenceType refType = location.declaringType();
    if (refType == null) {
      return null;
    }
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(myScratchFile);
    if (!(psiFile instanceof PsiJavaFile)) {
      return null;
    }
    final PsiClass[] classes = ((PsiJavaFile)psiFile).getClasses();
    if (classes.length == 0) {
      return null;
    }

    final String originalQName = refType.name();
    for (PsiClass aClass : classes) {
      if (StringUtil.equals(originalQName, aClass.getQualifiedName())) {
        return psiFile;
      }
    }

    final int dollar = originalQName.indexOf('$');
    final String alternativeQName = dollar > 0? originalQName.substring(0, dollar) : null;
    if (!StringUtil.isEmpty(alternativeQName)) {
      for (PsiClass aClass : classes) {
        if (StringUtil.equals(alternativeQName, aClass.getQualifiedName())) {
          return psiFile;
        }
      }
    }

    return null;
  }
}
