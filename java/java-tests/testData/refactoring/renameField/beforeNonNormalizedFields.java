public class TestRefactor
{
public String uiTest1 = new String("Test1"),
uiTest2<caret> = new String("Test2"),
uiTest3 = new String("Test1");
{ System.out.println(uiTest2);}

}