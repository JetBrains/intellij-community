import java.util.ArrayList;
import java.util.List;
class Test{
  static class Model<K>{
    Model( Model<? super K> model ){
      this( <warning descr="Unchecked assignment: 'java.util.List' to 'java.util.List<Test.Model<K>>'">list( <warning descr="Unchecked assignment: 'java.util.ArrayList' to 'java.util.List<Test.Model<? super java.lang.Object>>'">(ArrayList)model.get()</warning> )</warning> );
    }
    Model( List<Model<K>> list ){
      System.out.println(list);
    }
    List<Model<K>> get(){
      return null;
    }
    static <T> List<Model<T>> list( List<Model<? super T>> list ){
      System.out.println(list);
      return null;
    }
  }
  public static void main(String[] args) {
    Model model = <warning descr="Unchecked call to 'Model(List<Model<K>>)' as a member of raw type 'Test.Model'">new Model(new ArrayList())</warning>;
    System.out.println(<warning descr="Unchecked call to 'Model(Model<? super K>)' as a member of raw type 'Test.Model'">new Model(model)</warning>);
  }
}