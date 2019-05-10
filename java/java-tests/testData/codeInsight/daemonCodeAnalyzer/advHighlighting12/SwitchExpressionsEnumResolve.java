import java.util.Random;

class SwitchExpressionsEnumResolve {
  enum E { E1, E2 }
  
  E test(E e) {
    return switch (e) {
      case E1 -> <error descr="Cannot resolve symbol 'E2'">E2</error>;
      case E2 -> <error descr="Cannot resolve symbol 'E1'">E1</error>;
    };
  }
}