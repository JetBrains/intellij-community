// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import com.jetbrains.rhizomedb.ChangeScope
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.EntityType
import com.jetbrains.rhizomedb.Novelty

fun interface OfferContributor {
  fun ChangeScope.contribute(novelty: Novelty)
}

internal fun offerContributors(): List<OfferContributor> {
  return OfferContributorEntity.all().map { it.contributor }
}

internal data class OfferContributorEntity(override val eid: EID) : Entity {
  val contributor by ContributorAttr

  companion object : EntityType<OfferContributorEntity>(OfferContributorEntity::class, ::OfferContributorEntity) {
    val ContributorAttr = requiredTransient<OfferContributor>("contributor")
  }
}

fun ChangeScope.offerContributorInternal(contributor: OfferContributor) {
  OfferContributorEntity.new {
    it[OfferContributorEntity.ContributorAttr] = contributor
  }
}