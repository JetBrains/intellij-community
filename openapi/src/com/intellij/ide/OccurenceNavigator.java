package com.intellij.ide;

import com.intellij.pom.Navigatable;

public interface OccurenceNavigator {
  public class OccurenceInfo {
    private final Navigatable myNavigateable;
    private final int myOccurenceNumber;
    private final int myOccurencesCount;

    public OccurenceInfo(Navigatable navigateable, int occurenceNumber, int occurencesCount) {
      myNavigateable = navigateable;
      myOccurenceNumber = occurenceNumber;
      myOccurencesCount = occurencesCount;
    }

    private OccurenceInfo(int occurenceNumber, int occurencesCount) {
      this(null, occurenceNumber, occurencesCount);
    }

    public static OccurenceInfo position(int occurenceNumber, int occurencesCount) {
      return new OccurenceInfo(occurenceNumber, occurencesCount);
    }

    public Navigatable getNavigateable() {
      return myNavigateable;
    }

    public int getOccurenceNumber() {
      return myOccurenceNumber;
    }

    public int getOccurencesCount() {
      return myOccurencesCount;
    }
  }

  boolean hasNextOccurence();
  boolean hasPreviousOccurence();
  OccurenceInfo goNextOccurence();
  OccurenceInfo goPreviousOccurence();
  String getNextOccurenceActionName();
  String getPreviousOccurenceActionName();
}
