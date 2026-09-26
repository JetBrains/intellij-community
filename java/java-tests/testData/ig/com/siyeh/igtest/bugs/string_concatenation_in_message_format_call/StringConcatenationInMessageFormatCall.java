import java.text.MessageFormat;

public class StringConcatenationInMessageFormatCall {

  String formatGreeting(String userName, int balance) {
    return MessageFormat.format(<warning descr="String concatenation as argument to 'MessageFormat.format()' call">"Hello, " + <caret>userName +
                                "! Your balance is {0}."</warning>, balance);
  }
}