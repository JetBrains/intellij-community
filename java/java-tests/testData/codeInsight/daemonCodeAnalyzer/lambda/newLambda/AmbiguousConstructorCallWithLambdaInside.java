import java.util.function.Function;
import java.util.function.Supplier;

class OverloadCast {

    public void runMe() {
        new <error descr="Cannot resolve constructor 'OverloadCast(<method reference>, <lambda expression>)'">OverloadCast</error>(WhitespaceTokenizer::<error descr="Cannot resolve constructor 'WhitespaceTokenizer'">new</error>, src -> new LowerCaseFilter<error descr="'LowerCaseFilter(OverloadCast.TokenStream)' in 'OverloadCast.LowerCaseFilter' cannot be applied to '(<lambda parameter>)'">(src)</error>);
        <error descr="Ambiguous method call: both 'OverloadCast.overloadCast(Supplier<Tokenizer>, Function<TokenStream, TokenFilter>)' and 'OverloadCast.overloadCast(Function<TokenStream, TokenFilter>, Function<String, String>)' match">overloadCast</error>(WhitespaceTokenizer::<error descr="Cannot resolve constructor 'WhitespaceTokenizer'">new</error>, src -> new LowerCaseFilter<error descr="'LowerCaseFilter(OverloadCast.TokenStream)' in 'OverloadCast.LowerCaseFilter' cannot be applied to '(<lambda parameter>)'">(src)</error>);
    }

    private OverloadCast(Supplier<Tokenizer> tokenizerFactory, Function<TokenStream, TokenFilter> filterCreator) {
    }

    private OverloadCast(Function<TokenStream, TokenFilter> filterCreator, Function<String, String> readerWrapper) {
    }

    private void overloadCast(Supplier<Tokenizer> tokenizerFactory, Function<TokenStream, TokenFilter> filterCreator) {
    }

    private void overloadCast(Function<TokenStream, TokenFilter> filterCreator, Function<String, String> readerWrapper) {
    }

    private class Tokenizer {
    }

    private class TokenStream {
    }

    private class TokenFilter {
    }

    private class WhitespaceTokenizer extends Tokenizer {
        private WhitespaceTokenizer(TokenStream s) {
        }

        private WhitespaceTokenizer() {
        }
    }

    private class LowerCaseFilter extends TokenFilter {
        public LowerCaseFilter(TokenStream src) {
            super();
        }

        private LowerCaseFilter() {
        }
    }
}