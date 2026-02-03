package com.siyeh.igtest.bugs.infinite_recursion;

import java.util.List;
import java.io.IOException;
import java.io.File;
import java.util.Objects;

public class InfiniteRecursion
{
    public void foo()
    {
        new InfiniteRecursion().foo();
    }

    public void bar()
    {
        foo();
    }

    public int <warning descr="Method 'baz()' recurses infinitely, and can only end by throwing an exception">baz</warning>()
    {
        return baz();
    }

    public int bazoom()
    {
        if(foobar())
        {
            return bazoom();
        }
        return 3;
    }

    public void bazoomvoid()
    {
        if(foobar())
        {
            bazoomvoid();
        }
    }

    public int barangus()
    {
        while(foobar())
        {
            return barangus();
        }
        return 3;
    }

    public int <warning descr="Method 'barangoo()' recurses infinitely, and can only end by throwing an exception">barangoo</warning>()
    {
        do
        {
            return barangoo();
        }
        while(foobar());
    }

    public int <warning descr="Method 'bazoomer()' recurses infinitely, and can only end by throwing an exception">bazoomer</warning>()
    {
        if(foobar())
        {
            return bazoomer();
        }
        else
        {
            return bazoomer() + 3;
        }
    }

    public boolean foobar()
    {
        return false && foobar();
    }

    public boolean <warning descr="Method 'foobarangus()' recurses infinitely, and can only end by throwing an exception">foobarangus</warning>()
    {
        return foobarangus() && false;
    }

    public int bangem(PsiClass aClass)
    {
        final PsiClass superClass = aClass.getSuperClass();
        if(superClass ==null)
        {
            return 0;
        }
        else
        {
            return bangem(aClass)+1;
        }
    }

    private boolean foo(final PsiClass superClass)
    {
        return superClass ==null;
    }

    public int getInheritanceDepth(PsiClass aClass)
    {
        final PsiClass superClass = aClass.getSuperClass();
        if(superClass == null)
        {
            return 0;
        }
        else
        {
            return getInheritanceDepth(superClass) + 1;
        }
    }

     void rec(List pageConfig) {
        try {
            new File("c:/").getCanonicalFile();
        } catch (IOException e) {

        }
        for (int j = 0; j < pageConfig.size(); j++) {
            List pc = (List) pageConfig.get(j);
            rec(pc);
        }
    }

    void <warning descr="Method 'foo1()' recurses infinitely, and can only end by throwing an exception">foo1</warning>() {
        for (;true && true || false;) {
            foo1();
        }
    }

    void <warning descr="Method 'foo2()' recurses infinitely, and can only end by throwing an exception">foo2</warning>() {
        if (true || false) {
            foo2();
        }
    }

    void <warning descr="Method 'bar1()' recurses infinitely, and can only end by throwing an exception">bar1</warning>() {
        while (true || false) {
            bar1();
        }
    }
}

interface PsiClass {
    PsiClass getSuperClass();
}

class IndirectRecursion {

  @Override
  public int <warning descr="Method 'hashCode()' recurses infinitely, and can only end by throwing an exception">hashCode</warning>() {
    return Objects.hashCode(this);
  }

  @Override
  public boolean <warning descr="Method 'equals()' recurses infinitely, and can only end by throwing an exception">equals</warning>(Object obj) {
    return Objects.equals(obj, this);
  }

  @Override
  public String <warning descr="Method 'toString()' recurses infinitely, and can only end by throwing an exception">toString</warning>() {
    return String.valueOf(this);
  }
}

class IndirectRecursion2 {
  int i = 1;

  @Override
  public int <warning descr="Method 'hashCode()' recurses infinitely, and can only end by throwing an exception">hashCode</warning>() {
    if (i == 1) {
      return Objects.hashCode(this);
    }
    return Objects.hashCode(this);
  }
}

class IndirectRecursionNoWarning {

  void brokenCode(int i) {
    brokenCode<error descr="Expected 1 argument but found 2">(1, 2)</error>;
  }

  int i = 1;
  @Override
  public int hashCode() {
    if (i == 1) {
      return 0;
    }
    return Objects.hashCode(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (i == 1) {
      return true;
    }
    return Objects.equals(obj, this);
  }

  @Override
  public String toString() {
    if (i == 1) {
      return "";
    }
    return String.valueOf(this);
  }
}

class Switch{
  int i = 1;

  public int t1() {
    switch (i) {
      case 1 ->{
        System.out.println(1);
        return 1;
      }
      default -> System.out.println(1);
    }
    t1();
    return 1;
  }
  public int <warning descr="Method 't2()' recurses infinitely, and can only end by throwing an exception">t2</warning>() {
    switch (i) {
      case 1 -> System.out.println(1);
      case 2 -> System.out.println(2);
    }
    t2();
    return 1;
  }
  public int t3() {
    switch (i) {
      case 1: {
        System.out.println(1);
        return 1;
      }
      default: System.out.println(1);
    }
    t3();
    return 1;
  }
  public int <warning descr="Method 't4()' recurses infinitely, and can only end by throwing an exception">t4</warning>() {
    switch (i) {
      case 1: System.out.println(1);
      case 2: System.out.println(2);
    }
    t4();
    return 1;
  }
}