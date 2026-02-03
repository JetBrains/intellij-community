// LocalsOrMyInstanceFieldsControlFlowPolicy
// SCR 12926 

import java.rmi.RemoteException;

public class B {
    public Object show(Object obj)
    {
        try
        {
            throw new NullPointerException();
        }
        catch ( NullPointerException npe )
        {
        }
        finally
        {<caret>
            if ( obj != null )
            {
                try
                {
                    throw new RemoteException();
                }
                catch ( RemoteException re )
                {
                    System.out.println( re );
                }
                finally
                {
                    obj = null;
                }
            }
        }
        return null;
    }

}
