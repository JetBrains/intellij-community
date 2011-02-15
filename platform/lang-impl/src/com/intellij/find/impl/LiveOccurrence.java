package com.intellij.find.impl;

import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;
import java.util.Collection;

public class LiveOccurrence {
  private TextRange myPrimaryRange;
  private Collection<TextRange> mySecondaryRanges = new ArrayList<TextRange>();

  public TextRange getPrimaryRange() {
    return myPrimaryRange;
  }

  public Collection<TextRange> getSecondaryRanges() {
    return mySecondaryRanges;
  }

  public void setPrimaryRange(TextRange primaryRange) {
    this.myPrimaryRange = primaryRange;
  }

  public void setSecondaryRanges(Collection<TextRange> secondaryRanges) {
    this.mySecondaryRanges = secondaryRanges;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof LiveOccurrence) {
      if (myPrimaryRange.equals(((LiveOccurrence)o).getPrimaryRange())) {
        return true;
      }
    }
    return false;
  }
}
