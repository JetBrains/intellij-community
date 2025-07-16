import org.jetbrains.annotations.NotNull;

interface TweetParser {
    static String getTweetMessageFrom(String fullTweet) {
        String fieldName = "\"text\":\"";
        return newMethod(fullTweet, fieldName);
    }

    @NotNull
    static String newMethod(String fullTweet, String fieldName) {
        int indexOfField = fullTweet.indexOf(fieldName) + fieldName.length();
        int indexOfEndOfField = fullTweet.indexOf("\"", indexOfField);
        return fullTweet.substring(indexOfField, indexOfEndOfField);
    }

    static String getTwitterHandleFromTweet(String fullTweet) {
        String twitterHandleFieldName = "\"screen_name\":\"";
        return newMethod(fullTweet, twitterHandleFieldName);
    }
}