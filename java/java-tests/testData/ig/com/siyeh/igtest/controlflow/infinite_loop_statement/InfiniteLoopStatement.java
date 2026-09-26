package com.siyeh.igtest.controlflow.infinite_loop_statement;

public class InfiniteLoopStatement {

    void bla() {
        int x = 0;
        c:
        b:
        <warning descr="'while' statement cannot complete without throwing an exception">while</warning> (true) {
            if (x == 0) {
                a:
                while (true) { // A warning issued here
                    x++;
                    continue b;
                }
            }
            System.out.println("Loop");
        }
    }

    void notInfinite1(String s) {
        while (true) {
            if (s.equals("exit")) {
                System.exit(1);
            }
        }
    }

    void doWhile() {
        int x = 0;
        do {
            x++;
            continue;
        } while (x < 10);
    }

    void lambdaThreadRun() {
        new Thread((() -> {
            while (true) {}
        })).start();
    }

    void anonymClass() {
        new Thread((new Runnable() {
            @Override
            public void run() {
                while (true) {
                }
            }
        })).start();
    }

    void anotherPrivateMethod() {
        new Thread((() -> usedInThreadConstructor())).start();
    }

    private void usedInThreadConstructor() {
        while (true) {}
    }

    void privateMethodAsReference() {
        new Thread((this::alsoUsedInThreadConstructor)).start();
    }

    private void alsoUsedInThreadConstructor() {
        while (true) {}
    }

    void foo() {
        bar:
        {
            while (true) {
                break bar;
            }
        }
    }

    void testYield(int i){
      String text = switch (i){
        case 1 -> "test";
        default -> {
          while (true){
            if(i<1){
              yield "test2";
            }
            i--;
          }
        }
      };
      String text2 = switch (i){
        case 1 -> "test";
        default -> {
          <warning descr="'while' statement cannot complete without throwing an exception">while</warning> (true){
            String inner = switch (i){
              case 2 -> "test2";
              default -> {
                yield "test3";
              }
            };
          }
        }
      };
    }
}
