import org.jetbrains.providers.MyProviderImpl;
import org.jetbrains.providers.MySuperClass.MySubProviderImpl;
import org.jetbrains.providers.MyRecord;
import org.jetbrains.providers.WithProvider;
import org.jetbrains.providers.WrongPlace;
import org.jetbraons.api.MyProviderInterface;

module my.providers {
  uses MyProviderInterface;
  requires sub.module;
  provides MyProviderInterface with MyProviderImpl, MySubProviderImpl, MyRecord, WithProvider, WrongPlace;
}