// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::time::Instant;

use log::{LevelFilter, Metadata, Record, SetLoggerError};

pub fn init(filter: LevelFilter) -> Result<(), SetLoggerError> {
    let logger = Logger { start: Instant::now() };
    let result = log::set_boxed_logger(Box::new(logger));
    if result.is_ok() {
        log::set_max_level(filter);
    }
    result
}

struct Logger {
    start: Instant
}

impl log::Log for Logger {
    fn enabled(&self, _metadata: &Metadata<'_>) -> bool {
        true
    }

    fn log(&self, record: &Record<'_>) {
        let ms = Instant::now().duration_since(self.start).as_millis();
        println!("{:4} [{:5}] {}: {}", ms, record.level(), record.target(), record.args());
    }

    fn flush(&self) {}
}
