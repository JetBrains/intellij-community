public class TestSwitchSelect
{
    public static void main(String[] args)
    {
        switch (args.length) {
            case 0:
                return;
            case 1:
                System.out.println("");
                System.out.println("");<caret>
                System.out.println(""); 
                System.out.println("");
            case 2:
                System.out.println("");
                System.out.println("");
                System.out.println(""); 
                System.out.println("");
        }
    }
} 