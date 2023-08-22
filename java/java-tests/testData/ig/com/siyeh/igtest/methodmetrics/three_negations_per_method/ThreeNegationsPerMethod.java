package com.siyeh.igtest.methodmetrics.three_negations_per_method;

public class ThreeNegationsPerMethod
{
    int foo, bar, baz;

    public void okayMethod()
    {
        if(!!!true)
        {
            return;
        }
    }

    public void <warning descr="'badMethod' contains 4 negations">badMethod</warning>()
    {
        if(!!!!true)
        {
            return;
        }
    }

    public void <warning descr="'badMethod2' contains 4 negations">badMethod2</warning>()
    {
        if(!!!true && 3 !=4)
        {
            return;
        }
    }

    public void <warning descr="'badMethod3' contains 4 negations">badMethod3</warning>() {
        if (true != false != true != false != true) {
            return;
        }
    }

    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ThreeNegationsPerMethod threeNegationsPerMethod = (ThreeNegationsPerMethod) o;

        if (bar != threeNegationsPerMethod.bar) return false;
        if (baz != threeNegationsPerMethod.baz) return false;
        if (foo != threeNegationsPerMethod.foo) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = foo;
        result = 29 * result + bar;
        result = 29 * result + baz;
        return result;
    }
}
class User {
  public User getUser(final String username, final String password )
  {
    if (!isUsernameValid(username))
    {
      throw new IllegalArgumentException("Invalid username!");
    }

    if (!isPasswordValid(password))
    {
      throw new IllegalArgumentException("Invalid password!");
    }

    assert (username != null) && (password != null);

    return searchLoginTable(username.toLowerCase(), password.toCharArray());
  }

  private User searchLoginTable(String s, char[] chars) {
    return null;
  }

  private boolean isPasswordValid(String password) {
    return true;
  }

  private boolean isUsernameValid(String username) {
    return true;
  }
}
