import java.text.MessageFormat;

public class StringConcatenationInMessageFormatCall {

  String formatGreeting(String userName, int balance) {
    return MessageFormat.format("Hello, {1}"<caret> +
            "! Your balance is {0}.", balance, userName);
  }
}