interface IRequestablePage {}
abstract class Page implements IRequestablePage {}
abstract class BTest {
  public final <C extends IRequestablePage> void setResponsePage(final Class<C> cls) {}
  public final <C extends IRequestablePage> void setResponsePage(final Class<C> cls, int i) {}
  public abstract Class<? extends Page> getHomePage();

  {
    setResponsePage(getHomePage());
  }

  public BTest(Class<? extends Page> homePage) {
    ALink link = new ALink() {
      {
        setResponsePage(homePage);
      }
    };
  }
}

class ALink<T> extends BTest {
  public ALink() {
    super(null);
  }

  @Override
  public Class<? extends Page> getHomePage() {
    return null;
  }
}