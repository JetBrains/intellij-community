// inherit from final
public class a extends <error descr="Cannot inherit from final class 'ff'">ff</error> {

  void f() {
    Object o = new <error descr="Cannot inherit from final class 'ff'">ff</error>() { void gg(){} };
  }
}

final class ff {
}
