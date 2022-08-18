public class TestSwitchSelect
{
    public static void main(String[] args)
    {
        switch (type) {
         case 1:
            // Selectable
            break;
      
         case 2:
            // Not <selection>selectable<caret></selection>
            break;
         default:
            // Gets selected along with case 2
            break;
      } 
    }
} 