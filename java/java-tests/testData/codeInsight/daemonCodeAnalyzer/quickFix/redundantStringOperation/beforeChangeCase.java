// "Fix all 'Redundant 'String' operation' problems in file" "true"
import java.util.Locale;
public class Main {
  public static void main(String[] args) {


    boolean value = args[0].toLowe<caret>rCase().equals("foo");
    boolean value2 ="foo".equals(args[0].toLowerCase()) ;
    boolean value3 = args[0].toLowerCase().toLowerCase().equals(args[0].toLowerCase().toLowerCase());

    boolean value1WithBrackets = (args[0]).toLowerCase().equals(("foo"));
    boolean value2WithBrackets = ("foo").equals((args[0].toLowerCase()));
    boolean value3WithBrackets = (args[0].toLowerCase().toLowerCase()).equals((args[0].toLowerCase().toLowerCase()));

    boolean valueUpperCase1 = args[0].toUpperCase().equals("FOO");
    boolean valueUpperCase2 ="FOO".equals(args[0].toUpperCase()) ;
    boolean valueUpperCase3 = args[0].toUpperCase().equals(args[0].toUpperCase());

    boolean valueIgnoreCase = args[0].toLowerCase().equalsIgnoreCase("foo");
    boolean valueIgnoreCase2 ="foo".equalsIgnoreCase(args[0].toLowerCase()) ;
    boolean valueIgnoreCase3 = args[0].toLowerCase().toLowerCase().equalsIgnoreCase(args[0].toLowerCase().toLowerCase());

    boolean valueIgnoreCaseUpperCase1 = args[0].toUpperCase().equalsIgnoreCase("FOO");
    boolean valueIgnoreCaseUpperCase2 ="FOO".equalsIgnoreCase(args[0].toUpperCase()) ;
    boolean valueIgnoreCaseUpperCase3 = args[0].toUpperCase().equalsIgnoreCase(args[0].toUpperCase());

    boolean nonChangeValue1 = args[0].toLowerCase(Locale.ROOT).equalsIgnoreCase("foo");
    boolean nonChangeValue2 = args[0].toLowerCase(Locale.ENGLISH).equalsIgnoreCase("foo");
    boolean nonChangeValue3 ="foo".equalsIgnoreCase(args[0].toUpperCase().toLowerCase(Locale.ENGLISH)) ;
    boolean nonChangeValue4 = args[0].toLowerCase().toLowerCase(Locale.ENGLISH).equalsIgnoreCase(args[0].toLowerCase().toLowerCase());
    boolean nonChangeValue5 = args[0].toLowerCase().toLowerCase().equalsIgnoreCase(args[0].toLowerCase().toLowerCase(Locale.ENGLISH));
    boolean nonChangeValue6 = args[0].toLowerCase().toLowerCase().equalsIgnoreCase(args[0].toLowerCase().toUpperCase());
    boolean nonChangeValue7 = args[0].toLowerCase(Locale.ROOT).equalsIgnoreCase("foo");
    boolean nonChangeValue8 = args[0].toLowerCase(Locale.ENGLISH).equalsIgnoreCase("foo");
    boolean nonChangeValue9 ="foo".equalsIgnoreCase(args[0].toUpperCase().toLowerCase(Locale.ENGLISH)) ;
    boolean nonChangeValue10 = args[0].toLowerCase().toLowerCase(Locale.ENGLISH).equalsIgnoreCase(args[0].toLowerCase().toLowerCase());
    boolean nonChangeValue11 = args[0].toLowerCase().toLowerCase().equalsIgnoreCase(args[0].toLowerCase().toLowerCase(Locale.ENGLISH));
    boolean nonChangeValue12 = args[0].toLowerCase().toLowerCase().equalsIgnoreCase(args[0].toLowerCase().toUpperCase());

    if(args[0].toLowerCase().equals("foo")){}
    if("foo".equals(args[0].toUpperCase().toLowerCase())){}
    if( args[0].toLowerCase().toLowerCase().equals(args[0].toLowerCase().toLowerCase())){}

    if(!args[0].toLowerCase().equals("foo")){}
    if(!"foo".equals(args[0].toUpperCase().toLowerCase())){}
    if(! args[0].toLowerCase().toLowerCase().equals(args[0].toLowerCase().toLowerCase())){}

    if(!!args[0].toLowerCase().equals("foo")){}
    if(!!"foo".equals(args[0].toUpperCase().toLowerCase())){}
    if(!! args[0].toLowerCase().toLowerCase().equals(args[0].toLowerCase().toLowerCase())){}


    if(args[0].toUpperCase().equals("FOO")){}
    if("FOO".equals(args[0].toUpperCase().toUpperCase())){}
    if( args[0].toUpperCase().toUpperCase().equals(args[0].toLowerCase().toUpperCase())){}

    if(args[0].toLowerCase().equalsIgnoreCase("foo")){}
    if("foo".equalsIgnoreCase(args[0].toUpperCase().toLowerCase())){}
    if( args[0].toLowerCase().toLowerCase().equalsIgnoreCase(args[0].toLowerCase().toLowerCase())){}

    if(args[0].toUpperCase().equalsIgnoreCase("FOO")){}
    if("FOO".equalsIgnoreCase(args[0].toUpperCase().toUpperCase())){}
    if( args[0].toUpperCase().toUpperCase().equalsIgnoreCase(args[0].toLowerCase().toUpperCase())){}


    if(/* one */args/* two */[/* three */0/* four */]./* five */toLowerCase()/* six */./* seven */equals(/* eight */"foo" /* nine */)){}
    if(/* one */"foo"/* two */./* three */equals(/* four */args/* five */[/* six */0/* seven */]/* eight */./* nine */toLowerCase()/* ten */)){}
    if(/* one */args/* two */[/* three */0/* four */]./* five */toLowerCase()/* six */./* seven */equals(/* eight */args/* nine */[/* ten */1/* eleven */]/* twelve */./* thirteen */toLowerCase()/* fourteen */)){}
    if(!/* one */args/* two */[/* three */0/* four */]./* five */toLowerCase()/* six */./* seven */equals(/* eight */"foo" /* nine */)){}

    if("HELLO".equals(args[0].toLowerCase())) {}
    if("Hello".equals(args[0].toLowerCase())) {}
    if("hello".equals(args[0].toUpperCase())) {}
    if("Hello".equals(args[0].toUpperCase())) {}
  }
}
