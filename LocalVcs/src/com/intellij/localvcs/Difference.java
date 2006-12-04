package com.intellij.localvcs;

import com.intellij.openapi.vcs.checkin.DifferenceType;

import java.util.ArrayList;
import java.util.List;

public class Difference {
  public enum Kind {
    NOT_MODIFIED {
      public DifferenceType getDifferenceType() {
        return DifferenceType.NOT_CHANGED;
      }
    },
    MODIFIED {
      public DifferenceType getDifferenceType() {
        return DifferenceType.MODIFIED;
      }
    },
    CREATED {
      public DifferenceType getDifferenceType() {
        return DifferenceType.INSERTED;
      }
    },
    DELETED {
      public DifferenceType getDifferenceType() {
        return DifferenceType.DELETED;
      }
    };
    public abstract DifferenceType getDifferenceType();
  }

  private boolean myIsFile;
  private Kind myKind;
  private Entry myLeft;
  private Entry myRight;

  private List<Difference> myChildren = new ArrayList<Difference>();

  public Difference(boolean isFile, Kind k, Entry left, Entry right) {
    myIsFile = isFile;
    myKind = k;
    myLeft = left;
    myRight = right;
  }

  public boolean isFile() {
    return myIsFile;
  }

  public Kind getKind() {
    return myKind;
  }

  public DifferenceType getDifferenceType() {
    return myKind.getDifferenceType();
  }

  public Entry getLeft() {
    return myLeft;
  }

  public Entry getRight() {
    return myRight;
  }

  public void addChild(Difference d) {
    myChildren.add(d);
  }

  public List<Difference> getChildren() {
    return myChildren;
  }

  public boolean hasDifference() {
    if (!myKind.equals(Kind.NOT_MODIFIED)) return true;

    for (Difference child : myChildren) {
      if (child.hasDifference()) return true;
    }

    return false;
  }
}
