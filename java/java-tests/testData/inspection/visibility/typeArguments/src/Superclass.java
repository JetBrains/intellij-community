public abstract class Superclass<TParam extends AbstractSetupParams, TSetup extends AbstractSetup<TParam>>
{


   protected abstract TSetup createInitializer();


}
