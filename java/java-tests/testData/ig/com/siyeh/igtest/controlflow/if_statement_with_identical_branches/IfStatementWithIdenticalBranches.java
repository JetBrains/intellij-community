package com.siyeh.igtest.controlflow.if_statement_with_identical_branches;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {

    void simpleCases(boolean isValid) {
        if (isValid) {

        } else {

        }
        <weak_warning descr="'if' statement can be collapsed">if</weak_warning> (isValid) {
            System.out.println();
            return;
        }
        System.out.println();
    }

  void implicitElseInAForLoop() {
    for (int i = 0; i < 100; i++) {
      <weak_warning descr="'if' statement can be collapsed">if</weak_warning> (i == 10) {
        System.out.println("Next iteration");
        continue;
      }
      System.out.println("Next iteration");
    }
  }

    int sameCodeWithDifferentIdentifierNames(boolean isValid) {
        <weak_warning descr="'if' statement can be collapsed">if</weak_warning> (isValid) {
            int i = 2;
            return i;
        } else {
            int j = 2;
            return j;
        }
    }

    int differentCode(boolean isValid) {
        if (isValid) {
            int i = 3;
            return i;
        } else {
            int j = 4;
            return j;
        }
    }

    void impliciteJumpStatement(boolean isValid, boolean isActive) {
        if (isValid) {
            <weak_warning descr="'if' statement can be collapsed">if</weak_warning> (isActive) {
                System.out.println();
                return;
            }
        }
        System.out.println();
    }

    void nonLinearControlflow(boolean isValid) {
        boolean b = true;
        while (b) {
            if (isValid) {
                System.out.println();
            }
        }
        System.out.println();
    }

    void identicalCodeInSeveralBlocks(boolean isValid, boolean isActive) {
        if (isActive) {
            if (isValid) {
                System.out.println();
                System.out.println();
                return;
            }
            System.out.println();
        }
        System.out.println();
    }

    void notIdenticalJumpedCode(boolean isValid, boolean isActive) {
        if (isValid) {
            System.out.println();
            return;
        } else if (isActive) {
            System.out.println("different");
            return;
        }
        System.out.println();
    }

    void notIdenticalCode(boolean isValid, boolean isActive) {
        if (isValid) {
            System.out.println();
        } else if (isActive) {
            System.out.println("different");
        } else {
            System.out.println();
        }
    }

    void longIdenticalCode(boolean isValid, boolean isActive) {
        if (isValid) {

        } else if (isActive) {

        } else if (true) {

        } else {

        }
    }

  void blocks(boolean isValid) {
    <weak_warning descr="'if' statement can be collapsed">if</weak_warning> (isValid) {
      System.out.println();
      return;
    }
    System.out.println();
  }
}

class NotADup {
    public String getElementDescription(Object element, Collection location) {
        if (location instanceof List) {
            if (element instanceof String) {
                return notNullize(element);
            }
        } else if (location instanceof Set) {
            if (element instanceof String) {
                return message((String)element);
            }
        }
        return null;
    }

    private String notNullize(Object element) {
        return null;
    }

    private String message(String element) {
        return null;
    }

  public static String calculate(int someNumber) {
    if (someNumber == 0 ) {
      try {
        return placeOrder(3, null);
      }
      catch( Exception e ) {
        System.out.println("e = " + e);
      }
    }
    else if (someNumber == 1) {
      try {
        return placeOrder(3, someNumber, null);
      }
      catch(Exception e ) {
        System.out.println("e = " + e);
      }
    }
    return null;
  }

  private static String placeOrder(int i, int someNumber, Object o) {
    return null;
  }

  private static String placeOrder(int i, Object o) {
    return null;
  }

  void m() {
    int j;
    <weak_warning descr="'if' statement can be collapsed">if</weak_warning> (true) {
      j = 2;
    }
    else {
      j = 2;
    }
    System.out.println("j = " + j);
  }

  void n(int i) {
    if (i == 0) {
      System.out.println(((i)));
      ;
      ;
      {
      }
    }
    else System.out.println(i);
  }

  public static String o(List<String> list) {
    String tmp = null;
    for (final String comp : list) {
      if (!comp.contains("bad")) {
        return comp;
      } else if (tmp == null) {
        tmp = comp;
      }
    }
    return tmp;
  }

  Object foo() {
    Object a = new Object();

    while (true) {
      Object b = bar(a);
      if (b == a) {
        return b;
      }
      else {
        a = b;
      }
    }
  }

  private Object bar(Object x) {
    return null;
  }

  void nesting(int i, int j) {
    if (i == 2) {
      System.out.println("2");
    } else {
      if (j == 2) {
        System.out.println("2");
      }
    }
  }
}
class Poly {
  Poly rotateRight() {
    return this;
  }

  public Poly rotate(boolean rotateRight) {
    // Code inspection claims that the following if statement has identical branches, but that is incorrect.
    if (rotateRight) {
      return rotateRight();
    } else {
      return rotateRight().rotateRight().rotateRight();
    }
  }
}