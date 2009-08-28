package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;

import java.util.List;

public class CompositeBlockWrapper extends AbstractBlockWrapper{
  private List<AbstractBlockWrapper> myChildren;
  //private static final CodeStyleSettings.IndentOptions DEF_OPTIONS = new CodeStyleSettings.IndentOptions();

  public CompositeBlockWrapper(final Block block, final WhiteSpace whiteSpace, final CompositeBlockWrapper parent, TextRange textRange) {
    super(block, whiteSpace, parent, textRange);
  }

  public List<AbstractBlockWrapper> getChildren() {
    return myChildren;
  }

  public void setChildren(final List<AbstractBlockWrapper> children) {
    myChildren = children;
  }

  public void reset() {
    super.reset();

    if (myChildren != null) {
      for(AbstractBlockWrapper wrapper:myChildren) wrapper.reset();
    }
  }

  protected boolean indentAlreadyUsedBefore(final AbstractBlockWrapper child) {
    for (AbstractBlockWrapper childBefore : myChildren) {
      if (childBefore == child) return false;
      if (childBefore.getWhiteSpace().containsLineFeeds()) return true;      
    }
    return false;
  }

  public void dispose() {
    super.dispose();
    myChildren = null;
  }

  public AbstractBlockWrapper getPrevIndentedSibling(final AbstractBlockWrapper current) {
    AbstractBlockWrapper candidate = null;
    for (AbstractBlockWrapper child : myChildren) {
      if (child.getStartOffset() >= current.getStartOffset()) return candidate;
      if (child.getWhiteSpace().containsLineFeeds()) candidate = child;
    }

    return candidate;
  }

  /*
  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    for (AbstractBlockWrapper child : myChildren) {
      result.append(child.getWhiteSpace().generateWhiteSpace(DEF_OPTIONS)).append(child.toString());
    }
    return result.toString();
  } */
}
