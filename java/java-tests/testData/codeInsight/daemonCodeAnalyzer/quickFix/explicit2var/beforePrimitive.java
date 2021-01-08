// "Replace explicit type with 'var'" "true"
class Main {
  {
    @I <caret>int i = 0;
  }
}

@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE, java.lang.annotation.ElementType.LOCAL_VARIABLE})
@interface I {}