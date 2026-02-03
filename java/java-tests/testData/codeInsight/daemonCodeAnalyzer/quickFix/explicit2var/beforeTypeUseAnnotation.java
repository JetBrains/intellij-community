// "Replace explicit type with 'var'" "false"
class Main {
  {
    @I <caret>int i = 0;
  }
}

@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE})
@interface I {}