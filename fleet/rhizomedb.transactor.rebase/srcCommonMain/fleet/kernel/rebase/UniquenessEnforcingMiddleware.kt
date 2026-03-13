package fleet.kernel.rebase

import com.jetbrains.rhizomedb.AllParts
import com.jetbrains.rhizomedb.ChangeScope
import com.jetbrains.rhizomedb.enforcingUniquenessConstraints
import fleet.kernel.TransactorMiddleware

// TBX-17470 - Leader middleware is not enforcing uniqueness on its own, and Toolbox is not using TransactorView
object UniquenessEnforcingMiddleware : TransactorMiddleware {
  override fun ChangeScope.performChange(next: ChangeScope.() -> Unit) {
    context.alter(context.impl.enforcingUniquenessConstraints(AllParts)) {
      next()
    }
  }

  context(cs: ChangeScope)
  override fun initDb() {
  }
}
