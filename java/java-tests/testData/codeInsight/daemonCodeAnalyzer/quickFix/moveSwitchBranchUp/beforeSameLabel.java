// "Move switch branch 'String _' before 'CharSequence _'" "false"
class Main {
  public static void main2(Object o) {
    int x = switch (o) {
      case CharSequence _, String<caret> _ ->2;
      case Integer _, Object _ when 2==2 -> 2;
    };
  }
}