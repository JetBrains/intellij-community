
import java.util.ArrayList;

abstract class Test {
  public void some(final ArrayList<ProcessingRule<String>> rules,
                   final Context context) {
    rules.add (createRule(context));
  }
  
  abstract <E extends CharSequence> ProcessingRule<E> createRule(Context<E> context);
}

interface ProcessingRule<T> {}
class Context<T> {}