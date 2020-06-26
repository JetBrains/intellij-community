// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.diff.*;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.ide.diff.DirDiffOperation.*;

/**
 * @author Konstantin Bulenkov
 */
public final class DirDiffElementImpl implements DirDiffElement {
  private final DTree myParent;
  private DiffType myType;
  private DiffElement mySource;
  private long mySourceLength;
  private DiffElement myTarget;
  private long myTargetLength;
  private final String myName;
  private DirDiffOperation myOperation;
  private DirDiffOperation myDefaultOperation;
  private final DTree myNode;

  private DirDiffElementImpl(DTree parent, @Nullable DiffElement source, @Nullable DiffElement target, DiffType type, String name,
                         @Nullable DirDiffOperation defaultOperation) {
    myParent = parent.getParent();
    myNode = parent;
    myType = type;
    mySource = source;
    mySourceLength = source == null || source.isContainer() ? -1 : source.getSize();
    myTarget = target;
    myTargetLength = target == null || target.isContainer() ? -1 : target.getSize();
    myName = name;
    if(defaultOperation != null){
      myDefaultOperation = defaultOperation;
    }
    else if (type == DiffType.ERROR) {
      myDefaultOperation = NONE;
    }
    else if (isSource()) {
      myDefaultOperation = COPY_TO;
    }
    else if (isTarget()) {
      myDefaultOperation = COPY_FROM;
    }
    else if (type == DiffType.EQUAL) {
      myDefaultOperation = EQUAL;
    }
    else if (type == DiffType.CHANGED) {
      assert source != null;
      myDefaultOperation = MERGE;
    }
  }

  public String getSourceModificationDate() {
    return mySource == null ? "" : getLastModification(mySource);
  }

  public String getTargetModificationDate() {
    return myTarget == null ? "" : getLastModification(myTarget);
  }

  public void updateTargetData() {
    if (myTarget != null && !myTarget.isContainer()) {
      myTargetLength = myTarget.getSize();
    } else {
      myTargetLength = -1;
    }
  }

  private static String getLastModification(DiffElement file) {
    final long timeStamp = file.getTimeStamp();
    return timeStamp < 0 ? "" : DateFormatUtil.formatDateTime(timeStamp);
  }

  public static DirDiffElementImpl createChange(DTree parent,
                                            @NotNull DiffElement source,
                                            @NotNull DiffElement target,
                                            @Nullable DirDiffSettings.CustomSourceChooser customSourceChooser) {
    DirDiffOperation defaultOperation = null;
    if (customSourceChooser != null) {
      DiffElement chosenSource = customSourceChooser.chooseSource(source, target);
      if (chosenSource == source) {  // chosenSource might be null
        defaultOperation = COPY_TO;
      }
      else if (chosenSource == target) {
        defaultOperation = COPY_FROM;
      }
    }
    return new DirDiffElementImpl(parent, source, target, DiffType.CHANGED, source.getPresentableName(), defaultOperation);
  }

  public static DirDiffElementImpl createError(DTree parent, @Nullable DiffElement source, @Nullable DiffElement target) {
    return new DirDiffElementImpl(parent, source, target, DiffType.ERROR, source == null ? target.getPresentableName() : source.getPresentableName(), null);
  }

  public static DirDiffElementImpl createSourceOnly(DTree parent, @NotNull DiffElement source) {
    return new DirDiffElementImpl(parent, source, null, DiffType.SOURCE, null, null);
  }

  public static DirDiffElementImpl createTargetOnly(DTree parent, @NotNull DiffElement target) {
    return new DirDiffElementImpl(parent, null, target, DiffType.TARGET, null, null);
  }

  public static DirDiffElementImpl createDirElement(DTree parent, DiffElement src, DiffElement trg, String name) {
    return new DirDiffElementImpl(parent, src, trg, DiffType.SEPARATOR, name, null);
  }

  public static DirDiffElementImpl createEqual(DTree parent, @NotNull DiffElement source, @NotNull DiffElement target) {
    return new DirDiffElementImpl(parent, source, target, DiffType.EQUAL, source.getPresentableName(), null);
  }

  @Override
  public DiffType getType() {
    return myType;
  }

  @Override
  public DiffElement getSource() {
    return mySource;
  }

  @Override
  public DiffElement getTarget() {
    return myTarget;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  public String getSourceName() {
    return mySource == null ? null : mySource.getName();
  }

  @Nullable
  public String getSourcePresentableName() {
    return mySource == null ? null : mySource.getPresentableName();
  }

  @Nullable
  public String getSourceSize() {
    return mySourceLength < 0 ? null : String.valueOf(mySourceLength);
  }

  public DirDiffOperation getDefaultOperation() {
    return myDefaultOperation;
    //if (myType == DType.SOURCE) return COPY_TO;
    //if (myType == DType.TARGET) return COPY_FROM;
    //if (myType == DType.CHANGED) return MERGE;
    //if (myType == DType.EQUAL) return EQUAL;
    //return NONE;
  }

  @Nullable
  public String getTargetName() {
    return myTarget == null ? null : myTarget.getName();
  }

  @Nullable
  public String getTargetPresentableName() {
    return myTarget == null ? null : myTarget.getPresentableName();
  }

  @Nullable
  public String getTargetSize() {
    return myTargetLength < 0 ? null : String.valueOf(myTargetLength);
  }

  public boolean isSeparator() {
    return myType == DiffType.SEPARATOR;
  }

  public boolean isSource() {
    return myType == DiffType.SOURCE;
  }

  public boolean isTarget() {
    return myType == DiffType.TARGET;
  }

  @Override
  public DirDiffOperation getOperation() {
    return myOperation == null ? myDefaultOperation : myOperation;
  }

  public void updateSourceFromTarget(DiffElement target) {
    myTarget = target;
    myTargetLength = mySourceLength;
    myDefaultOperation = EQUAL;
    myOperation = EQUAL;
    myType = DiffType.EQUAL;
  }

  public void updateTargetFromSource(DiffElement source) {
    mySource = source;
    mySourceLength = myTargetLength;
    myDefaultOperation = EQUAL;
    myOperation = EQUAL;
    myType = DiffType.EQUAL;
  }

  public void setNextOperation() {
    final DirDiffOperation op = getOperation();
    if (myType == DiffType.SOURCE) {
      myOperation = op == COPY_TO ? DELETE : op == DELETE ? NONE : COPY_TO;
    } else if (myType == DiffType.TARGET) {
      myOperation = op == COPY_FROM ? DELETE : op == DELETE ? NONE : COPY_FROM;
    } else if (myType == DiffType.CHANGED) {
      myOperation = op == MERGE ? COPY_TO : op == COPY_TO ? COPY_FROM : MERGE;
    }
  }

  public void setOperation(@NotNull DirDiffOperation operation) {
    if (myType == DiffType.EQUAL || myType == DiffType.SEPARATOR) return;
    if (myType == DiffType.TARGET && operation == COPY_TO) return;
    if (myType == DiffType.SOURCE && operation == COPY_FROM) return;
    if (myType == DiffType.CHANGED && operation == DELETE) return;

    myOperation = operation;
  }

  public Icon getSourceIcon() {
    return getIcon(mySource);
  }

  public Icon getTargetIcon() {
    return getIcon(myTarget);
  }

  private static Icon getIcon(DiffElement element) {
    return element != null ? element.getIcon() : null;
  }

  public DTree getParentNode() {
    return myParent;
  }

  public DTree getNode() {
    return myNode;
  }
}
