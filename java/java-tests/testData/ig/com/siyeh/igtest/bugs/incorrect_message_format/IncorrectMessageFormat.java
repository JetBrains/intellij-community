import java.text.MessageFormat;

class MessagePatternsExample {
  public static void main(String[] args) {
    String format = MessageFormat.format("{0}", 1, <warning descr="Argument with index '1' is not used in the pattern">2</warning>); //warning
    System.out.println(format);

    format = MessageFormat.format("{0}", 1);
    System.out.println(format);

    format = MessageFormat.<warning descr="No argument for index '2'">format</warning>("{2}", <warning descr="Argument with index '0' is not used in the pattern">1</warning>, <warning descr="Argument with index '1' is not used in the pattern">2</warning>); //warning
    System.out.println(format);

    format = MessageFormat.format("<weak_warning descr="Unpaired quote in message pattern">'</weak_warning>{0}", <warning descr="Argument with index '0' is not used in the pattern">1</warning>); //warning
    System.out.println(format);

    format = MessageFormat.<warning descr="No arguments for indexes: 2, 3">format</warning>("{2}, {3}", <warning descr="Argument with index '0' is not used in the pattern">1</warning>, <warning descr="Argument with index '1' is not used in the pattern">2</warning>); //warning

    MessageFormat messageFormat = new MessageFormat("<weak_warning descr="Unpaired quote in message pattern">'</weak_warning>{0}"); //warning

    format = MessageFormat.format("'{0}' it<weak_warning descr="Probably incorrect number of quotes, more than 1 quote will be printed">''''</weak_warning>s", <warning descr="Argument with index '0' is not used in the pattern">1</warning>); //warning
    System.out.println(format);

    format = MessageFormat.format("{0} <warning descr="Unmatched brace">{</warning>", 1);  //runtime exception
    System.out.println(format);

    format = MessageFormat.format("{0} {<warning descr="Incorrect index '1a'">1a</warning>}", 1);  //runtime exception
    System.out.println(format);

    format = MessageFormat.format("{<warning descr="Incorrect index '-1'">-1</warning>}", 1);  //runtime exception
    System.out.println(format);


    format = MessageFormat.format("{0,<warning descr="Unknown format type ' wrong'"> wrong</warning>}", 1);  //runtime exception
    System.out.println(format);

    format = MessageFormat.format("<weak_warning descr="Unclosed brace">{</weak_warning>0{", <warning descr="Argument with index '0' is not used in the pattern">1</warning>); //warning
    System.out.println(format);

    format = MessageFormat.format("<weak_warning descr="Unpaired quote in message pattern">'</weak_warning>{0}", <warning descr="Argument with index '0' is not used in the pattern">1</warning>); //warning
    System.out.println(format);

    format = MessageFormat.format("it<weak_warning descr="Probably incorrect number of quotes, more than 1 quote will be printed">''''</weak_warning>s {0}", 1); //warning
    System.out.println(format);

    format = MessageFormat.format("{0, choice, 0#0|1#1}", 1);
    System.out.println(format);

    format = MessageFormat.format("{0, choice,<warning descr="Lower bound ' a' in choice pattern is incorrect"> a</warning>#0|1#1}", 1);  //runtime exception
    System.out.println(format);

    format = MessageFormat.format("{0, choice,<warning descr="Empty lower bound in choice pattern">#</warning>0|1#1}", 1);  //runtime exception
    System.out.println(format);

    format = MessageFormat.format("{0, choice,0#0|0<1}", 1);
    System.out.println(format);

    format = MessageFormat.format("{0, choice,0#0|<warning descr="Lower bounds not in ascending order in choice pattern">-1</warning><1}", 1); //runtime exception
    System.out.println(format);
  }

  public static final String PATTERN = "<weak_warning descr="Unpaired quote in message pattern">'</weak_warning>{0}";

  public void testWithVariable(int i) {
    String pattern = "{0, wrong}";
    String format = MessageFormat.format(<warning descr="Unknown format type ' wrong' in message format pattern '{0, wrong}'">pattern</warning>, 1);  //runtime exception
    System.out.println(format);

    pattern = "{0{";
    format = MessageFormat.format(<weak_warning descr="Unclosed brace in message format pattern '{0{'">pattern</weak_warning>, <warning descr="Argument with index '0' is not used in the pattern">1</warning>); //warning
    System.out.println(format);

    if (i == 1) {
      pattern = "{-1}";
    }
    format = MessageFormat.format(pattern, 1);

    format = MessageFormat.format(PATTERN, <warning descr="Argument with index '0' is not used in the pattern">1</warning>); //warning
    System.out.println(format);

    String abc = MessageFormat.format("{1}{2}{3}{65}",6, 5, 4, 3,<error descr="Expression expected">)</error>;
  }

  public static String useArray(Object[] arr) {
    return MessageFormat.format("put{0}({2}, {1})", arr);
  }
}
