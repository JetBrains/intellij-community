import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
class Test {
  private List<String> query(String request) {
    System.out.println(request);
    return null;
  }
  private List<String> <warning descr="Private method 'query(java.lang.String, java.lang.Object)' is never used">query</warning>(String request, Object context) {
    System.out.println(request + context);
    return null;
  }

  private List<String> <warning descr="Private method 'query1()' is never used">query1</warning>(){ return null;}
  private List<String> <warning descr="Private method 'query1(java.lang.String)' is never used">query1</warning>(String request) {
    System.out.println(request);
    return null;
  }
  private List<String> <warning descr="Private method 'query1(java.lang.String, java.lang.Object)' is never used">query1</warning>(String request, Object context) {
    System.out.println(request + context);
    return null;
  }

  private static <Message, Reply> Set<Message> replyWith(Function<Message, List<Reply>> futureFn){
    System.out.println(futureFn);
    return null;
  }
  private static <Message, Reply> Set<Message> <warning descr="Private method 'replyWith(java.util.concurrent.Callable<java.util.List<Reply>>)' is never used">replyWith</warning>(Callable<List<Reply>> fn) {
    System.out.println(fn);
    return null;
  }

  {
    Set<String> m = replyWith(this::query);
    System.out.println(m);
    Set<String> m1 = replyWith<error descr="Ambiguous method call: both 'Test.replyWith(Function<String, List<String>>)' and 'Test.replyWith(Callable<List<String>>)' match">(this::query1)</error>;
    System.out.println(m1);
  }
}
