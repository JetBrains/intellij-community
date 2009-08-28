package com.intellij.pom.tree.events.impl;

import com.intellij.lang.ASTNode;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.TreeChange;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NonNls;

public class ChangeInfoImpl implements ChangeInfo {
  @NonNls private static final String[] TO_STRING = {"add", "remove", "replace", "changed"};

  public static ChangeInfoImpl create(short type, ASTNode changed){
    switch(type){
      case REPLACE:
        return new ReplaceChangeInfoImpl(changed);
      default:
        return new ChangeInfoImpl(type, changed);
    }
  }

  private final short type;
  private int myOldLength = 0;

  protected ChangeInfoImpl(short type, ASTNode changed){
    this.type = type;
    myOldLength = type != ADD ? ((TreeElement)changed).getNotCachedLength() : 0;
  }

  public int getChangeType(){
    return type;
  }

  public String toString(){
    return TO_STRING[getChangeType()];
  }

  public void compactChange(TreeChange change){
    for (final ASTNode treeElement : change.getAffectedChildren()) {
      final ChangeInfo changeByChild = change.getChangeByChild(treeElement);
      processElementaryChange(changeByChild, treeElement);
    }
  }

  public void processElementaryChange(final ChangeInfo changeByChild, final ASTNode treeElement) {
    switch(changeByChild.getChangeType()){
      case ADD:
        myOldLength -= ((TreeElement)treeElement).getNotCachedLength();
        break;
      case REMOVED:
        myOldLength += changeByChild.getOldLength();
        break;
      case REPLACE:
        myOldLength -= ((TreeElement)treeElement).getNotCachedLength();
        myOldLength += changeByChild.getOldLength();
        break;
      case CONTENTS_CHANGED:
        myOldLength -= ((TreeElement)treeElement).getNotCachedLength();
        myOldLength += changeByChild.getOldLength();
        break;
    }
  }

  public int getOldLength(){
    return myOldLength;
  }

  public void setOldLength(int oldTreeLength) {
    myOldLength = oldTreeLength;
  }

}
