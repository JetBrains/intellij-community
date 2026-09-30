use super::*;

#[test]
fn compose_writes_the_prefix_the_summed_count_and_the_parts() {
    assert_eq!(
        compose(&[3, 0, 0, 0, 0], 3, &[vec![10], vec![20, 21]]),
        [3, 0, 0, 0, 0, 0, 3, 10, 20, 21]
    );
    assert_eq!(compose::<&[u8]>(&[], 0x0102, &[]), [1, 2]);
}
