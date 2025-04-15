import java.io.InputStream;
import java.util.function.Function;


class ConstructorNotFound {

  public static void main(String[] args) {
    new SuggesterSpec2<><error descr="Cannot resolve constructor 'SuggesterSpec2(String, <method reference>)'">("test", SuggestionBuilder::new)</error>;
    new SuggesterSpec2<><error descr="Cannot resolve constructor 'SuggesterSpec2(String, <lambda expression>)'">("test", is -> new SuggestionBuilder(is))</error>;

    new SuggesterSpec<><error descr="Cannot resolve constructor 'SuggesterSpec(String, <lambda expression>)'">("test", is -> new SuggestionBuilder(is))</error>;
    new SuggesterSpec<><error descr="Cannot resolve constructor 'SuggesterSpec(String, <method reference>)'">("test", SuggestionBuilder::new)</error>;
  }

}

class SuggesterSpec<T> {
  public SuggesterSpec(String name, Function<InputStream, T> builderReader, int count) {

  }

  public SuggesterSpec(String count, Function<InputStream, T> builderReader, String name) {

  }
}

class SuggesterSpec2<T> {
  public SuggesterSpec2(String name, Function<InputStream, T> builderReader, int count) {

  }

  public SuggesterSpec2(int count, Function<InputStream, T> builderReader, String name) {

  }
}

class SuggestionBuilder {
  public SuggestionBuilder() {

  }

  public SuggestionBuilder(InputStream is) {

  }
}