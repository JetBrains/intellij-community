public class BrokenAlignment {

    public BrokenAlignment(String errorMessage) {
        <caret>if (StringUtils
                .contains(errorMessage, "'UK_ACCOUNT_USERNAME'")
    }
}
