package com.intellij.openapi.vcs.history;

public interface VcsRevisionNumber extends Comparable<VcsRevisionNumber>{

  VcsRevisionNumber NULL = new VcsRevisionNumber() {
    public String asString() {
      return "";
    }

    public int compareTo(VcsRevisionNumber vcsRevisionNumber) {
      return 0;
    }
  };

  class Int implements VcsRevisionNumber{
    private final int myValue;

    public Int(int value) {
      myValue = value;
    }

    public String asString() {
      return String.valueOf(myValue);
    }

    public int compareTo(VcsRevisionNumber vcsRevisionNumber) {
      if (vcsRevisionNumber instanceof VcsRevisionNumber.Int){
        return myValue - ((Int)vcsRevisionNumber).myValue;
      }
      return 0;
    }
  }

  class Long implements VcsRevisionNumber{
    private final long myValue;

    public Long(long value) {
      myValue = value;
    }

    public String asString() {
      return String.valueOf(myValue);
    }

    public int compareTo(VcsRevisionNumber vcsRevisionNumber) {
      if (vcsRevisionNumber instanceof VcsRevisionNumber.Long){
        return (int)(myValue - ((Long)vcsRevisionNumber).myValue);
      }
      return 0;
    }
  }

  String asString();
}
