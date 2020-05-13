interface TweetParser {
    static String getTweetMessageFrom(String fullTweet) {
        String fieldName = "\"text\":\"";
        <selection>int indexOfField = fullTweet.indexOf(fieldName) + fieldName.length();
        int indexOfEndOfField = fullTweet.indexOf("\"", indexOfField);
        return fullTweet.substring(indexOfField, indexOfEndOfField);</selection>
    }

    static String getTwitterHandleFromTweet(String fullTweet) {
        String twitterHandleFieldName = "\"screen_name\":\"";
        int indexOfTwitterHandleField = fullTweet.indexOf(twitterHandleFieldName) + twitterHandleFieldName.length();
        int indexOfEndOfTwitterHandle = fullTweet.indexOf("\"", indexOfTwitterHandleField);
        return fullTweet.substring(indexOfTwitterHandleField, indexOfEndOfTwitterHandle);
    }
}