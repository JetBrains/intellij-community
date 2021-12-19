// "Fix all 'Redundant 'String' operation' problems in file" "true"
import java.util.Locale;
public class Main {
  public static void main(String[] args) {


    boolean value = args[0].equalsIgnoreCase("foo");
    boolean value2 ="foo".equalsIgnoreCase(args[0]) ;
    boolean value3 = args[0].toLowerCase().equalsIgnoreCase(args[0].toLowerCase());

    boolean value1WithBrackets = (args[0]).equalsIgnoreCase(("foo"));
    boolean value2WithBrackets = ("foo").equalsIgnoreCase((args[0]));
    boolean value3WithBrackets = (args[0].toLowerCase()).equalsIgnoreCase((args[0].toLowerCase()));

    boolean valueUpperCase1 = args[0].equalsIgnoreCase("FOO");
    boolean valueUpperCase2 ="FOO".equalsIgnoreCase(args[0]) ;
    boolean valueUpperCase3 = args[0].equalsIgnoreCase(args[0]);

    boolean valueIgnoreCase = args[0].equalsIgnoreCase("foo");
    boolean valueIgnoreCase2 ="foo".equalsIgnoreCase(args[0]) ;
    boolean valueIgnoreCase3 = args[0].toLowerCase().equalsIgnoreCase(args[0].toLowerCase());

    boolean valueIgnoreCaseUpperCase1 = args[0].equalsIgnoreCase("FOO");
    boolean valueIgnoreCaseUpperCase2 ="FOO".equalsIgnoreCase(args[0]) ;
    boolean valueIgnoreCaseUpperCase3 = args[0].equalsIgnoreCase(args[0]);

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

    if(args[0].equalsIgnoreCase("foo")){}
    if("foo".equalsIgnoreCase(args[0].toUpperCase())){}
    if( args[0].toLowerCase().equalsIgnoreCase(args[0].toLowerCase())){}

    if(!args[0].equalsIgnoreCase("foo")){}
    if(!"foo".equalsIgnoreCase(args[0].toUpperCase())){}
    if(! args[0].toLowerCase().equalsIgnoreCase(args[0].toLowerCase())){}

    if(!!args[0].equalsIgnoreCase("foo")){}
    if(!!"foo".equalsIgnoreCase(args[0].toUpperCase())){}
    if(!! args[0].toLowerCase().equalsIgnoreCase(args[0].toLowerCase())){}


    if(args[0].equalsIgnoreCase("FOO")){}
    if("FOO".equalsIgnoreCase(args[0].toUpperCase())){}
    if( args[0].toUpperCase().equalsIgnoreCase(args[0].toLowerCase())){}

    if(args[0].equalsIgnoreCase("foo")){}
    if("foo".equalsIgnoreCase(args[0].toUpperCase())){}
    if( args[0].toLowerCase().equalsIgnoreCase(args[0].toLowerCase())){}

    if(args[0].equalsIgnoreCase("FOO")){}
    if("FOO".equalsIgnoreCase(args[0].toUpperCase())){}
    if( args[0].toUpperCase().equalsIgnoreCase(args[0].toLowerCase())){}


      /* five */
      if(/* one */args/* two */[/* three */0/* four */]/* six */./* seven */equalsIgnoreCase(/* eight */"foo" /* nine */)){}
      /* eight */
      /* nine */
      if(/* one */"foo"/* two */./* three */equalsIgnoreCase(/* four */args/* five */[/* six */0/* seven */]/* ten */)){}
      /* twelve */
      /* thirteen */
      /* five */
      if(/* one */args/* two */[/* three */0/* four */]/* six */./* seven */equalsIgnoreCase(/* eight */args/* nine */[/* ten */1/* eleven */]/* fourteen */)){}
      /* five */
      if(!/* one */args/* two */[/* three */0/* four */]/* six */./* seven */equalsIgnoreCase(/* eight */"foo" /* nine */)){}

    if("HELLO".equals(args[0].toLowerCase())) {}
    if("Hello".equals(args[0].toLowerCase())) {}
    if("hello".equals(args[0].toUpperCase())) {}
    if("Hello".equals(args[0].toUpperCase())) {}
  }
}
