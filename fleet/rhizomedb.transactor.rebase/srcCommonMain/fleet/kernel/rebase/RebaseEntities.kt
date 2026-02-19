package fleet.kernel.rebase

import com.jetbrains.rhizomedb.ChangeScope
import com.jetbrains.rhizomedb.register

context(cs: ChangeScope)
internal fun registerInternalEntities(){
  register(OfferContributorEntity)
  register(RemoteKernelConnectionEntity)
  register(WorkspaceClockEntity)
}