// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The SHA-256 of the packed jar of each recipe in `merge/tests.rs`. The comment there states why these digests are frozen,
//! and what a change in one means.

pub(crate) const GOLDEN_MODULE_ONLY: &str = "541f94c6720a58b3abc309099382a4d157cf3e485321c3003fb9be078e7d27cf";
pub(crate) const GOLDEN_KEEP_MANIFEST: &str = "622c52ad7098cee4a36c94665b8759f456f1d7cede38ce6872d28007536227eb";
pub(crate) const GOLDEN_LIBRARY_AND_MODULE: &str = "a09cee3fbb62c83656aef9122a7a6ec00f477ca4a09ff38781a722e395e35114";
pub(crate) const GOLDEN_FIRST_SOURCE_WINS: &str = "8a6a3781636d843471369ce70aaf4101c0da2a154b182375a4551543651c4a9e";
/// The coverage-agent recipe. Unlike the other digests, it was proved against the Go packer, on 2026-09-27.
pub(crate) const GOLDEN_COVERAGE_AGENT: &str = "9117a6509f964012d1c2d6088c300d80008d1a10df29093947133c02992d2553";
pub(crate) const GOLDEN_ASYMMETRIC_EXTRA: &str = "a8bd1e24180de52a9dc83e14b8951e68f86769999976b181119e5f6d91bc36e8";
