package com.siyeh.igtest.methodmetrics.cyclomatic_complexity;

public class CyclomaticComplexity
{
    public void <warning descr="Overly complex method 'fooBar()' (cyclomatic complexity = 12)">fooBar</warning>()
    {
        int i = 0;
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        System.out.println("i = " + i);
    }

    private boolean bar()
    {
        return true;
    }

    private boolean <warning descr="Overly complex method 'polyadic()' (cyclomatic complexity = 12)">polyadic</warning>(boolean a, boolean b, boolean c) {
        return a && b || c && a || b && c || a && b || c && a || b && c;
    }

    private void <warning descr="Overly complex method 'tryCatch()' (cyclomatic complexity = 15)">tryCatch</warning>() {
        try {
        } catch (ArithmeticException e) {
        } catch (ArrayStoreException e) {
        } catch (ClassCastException e) {
        } catch (IllegalArgumentException e) {
        } catch (NegativeArraySizeException e) {
        } catch (NullPointerException e) {
        } catch (UnsupportedOperationException e) {
        }
        try {
        } catch (ArithmeticException e) {
        } catch (ArrayStoreException e) {
        } catch (ClassCastException e) {
        } catch (IllegalArgumentException e) {
        } catch (NegativeArraySizeException e) {
        } catch (NullPointerException e) {
        } catch (UnsupportedOperationException e) {
        }
    }

    // from https://stackoverflow.com/questions/30240236/cyclomatic-complexity-of-switch-case-statement
    private void <warning descr="Overly complex method 'example()' (cyclomatic complexity = 5)">example</warning>(int n) {
      if (n >= 0) {
        switch(n) {
          case 0:
          case 1:
            System.out.println("zero or one\n");
            break;
          case 2:
            System.out.println("two\n");
            break;
          case 3:
          case 4:
            System.out.println("three or four\n");
            break;
        }
      }
      else {
        System.out.println("negative\n");
      }
    }

  private void <warning descr="Overly complex method 'example2()' (cyclomatic complexity = 5)">example2</warning>(int n) {
    if (n >= 0) {
      switch(n) {
        case 0:
        case 1:
          System.out.println("zero or one\n");
          break;
        case 2:
          System.out.println("two\n");
          break;
        case 3:
        case 4:
          System.out.println("three or four\n");
          break;
        default:
          System.out.println("more");
      }
    }
    else {
      System.out.println("negative\n");
    }
  }

  private void <warning descr="Overly complex method 'switchStatement1()' (cyclomatic complexity = 2)">switchStatement1</warning>(int n) {
      switch (n) {
        default:
        case 1:
          System.out.println(1);
      }
  }

    private void <warning descr="Overly complex method 'switchStatement2()' (cyclomatic complexity = 3)">switchStatement2</warning>(int i) {
      switch (i) {
        case 0:
        case 1:
          System.out.println(i);
        case 2:
          break;
        case 3:
        case 4:
        case 5:
        case 6:
        case 7:
        case 8:
        case 9:
        case 10:
        case 11:
        case 12:
        default:
          break;
      }
    }

    private void switchStatement3(int n) {
      switch (n) {
        case 1:
        case 2:
        default:
      }
    }
}
