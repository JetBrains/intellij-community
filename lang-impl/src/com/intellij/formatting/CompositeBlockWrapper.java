package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;

import java.util.List;

public class CompositeBlockWrapper extends AbstractBlockWrapper{
  private List<AbstractBlockWrapper> myChildren;

  public CompositeBlockWrapper(final Block block, final WhiteSpace whiteSpace, final AbstractBlockWrapper parent, TextRange textRange) {
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

  public void dispose() {
    super.dispose();
    myChildren = null;
  }
}
