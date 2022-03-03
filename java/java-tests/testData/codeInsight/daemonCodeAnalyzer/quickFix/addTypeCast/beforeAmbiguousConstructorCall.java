// "Cast 1st argument to 'Supplier<Tokenizer>'" "true"
import java.util.function.Function;
import java.util.function.Supplier;

class OverloadCast {

    public void runMe() {
        new Over<caret>loadCast(WhitespaceTokenizer::new, src -> new LowerCaseFilter(src));
    }

    private OverloadCast(Supplier<Tokenizer> tokenizerFactory, Function<TokenStream, TokenFilter> filterCreator) {
    }

    private OverloadCast(Function<TokenStream, TokenFilter> filterCreator, Function<String, String> readerWrapper) {
    }

    private class Tokenizer { }

    private class TokenStream { }

    private class TokenFilter { }

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