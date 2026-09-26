package com.siyeh.igtest.methodmetrics;

public class NestingDepth
{
    public void <warning descr="'fooBar' is overly nested (maximum nesting depth = 6)">fooBar</warning>()
    {
        if(bar())
        {
            if(bar())
            {
                if(bar())
                {
                    if(bar())
                    {
                        if(bar())
                        {
                            if(bar())
                            {

                            }
                        }
                    }
                }
            }
        }
    }

    private boolean bar()
    {
        return true;
    }

    void <warning descr="'lambdas' is overly nested (maximum nesting depth = 6)">lambdas</warning>() {
      Runnable r = () -> {
        Runnable s = () -> {
          Runnable t = () -> {
            Runnable u = () -> {
              for (String str : new String[] {""}) {
                str = switch (1) {
                  case 0 -> "zero";
                  case 1 -> "one";
                  default -> "many";
                };
              }
            };
          };
        };
      };
    }
}
