import java.util.List;


class RedundantCasts {
    List<TranslatingCompiler> myTranslators;
    void t() {
        b((List<Compiler>) (List)myTranslators);
    }

    void b(List<Compiler> l){}
}
interface Compiler{}
interface TranslatingCompiler extends Compiler{}
